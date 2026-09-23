package com.ichsprechedeutsch.vocabulary;

import com.ichsprechedeutsch.activity.ActivityRules;
import com.ichsprechedeutsch.activity.ActivityService;
import com.ichsprechedeutsch.activity.ActivityService.SiteActivity;
import com.ichsprechedeutsch.activity.ActivityStore;
import com.ichsprechedeutsch.activity.ActivityType;
import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.config.ActivityProperties;
import com.ichsprechedeutsch.config.SrsProperties;
import com.ichsprechedeutsch.content.ContentCatalog;
import com.ichsprechedeutsch.content.Word;
import com.ichsprechedeutsch.learning.LearningAnswerStore;
import com.ichsprechedeutsch.learning.LearningAnswerStore.Kind;
import com.ichsprechedeutsch.level.LevelService;
import com.ichsprechedeutsch.onboarding.OnboardingStore;
import com.ichsprechedeutsch.srs.Grade;
import com.ichsprechedeutsch.srs.SrsLadder;
import com.ichsprechedeutsch.srs.SrsLadder.Next;
import com.ichsprechedeutsch.vocabulary.VocabularyPlanner.DueCard;
import com.ichsprechedeutsch.vocabulary.VocabularyPlanner.Plan;
import com.ichsprechedeutsch.vocabulary.VocabularyStore.Progress;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kelime oturumu (SPEC 8.1). Tekrar oturumu SRS durumunu degistirir;
 * sonuclar seviye kaniti uretmez (K2, 8.5).
 *
 * <p>Kelime icerigi katalogdan gelir (K-004); veritabaninda yalniz
 * kullanicinin merdiven durumu ve verdigi cevaplar durur.
 */
@Service
public class VocabularyService {

    /**
     * Oturumdaki bir kart.
     *
     * @param isNew      ilk kez calisiliyor (tanitim karti)
     * @param askArticle isimse once artikel sorulur (Turkcede cinsiyet yok)
     */
    public record Card(String wordId, String lemma, String article, String plural, String partOfSpeech,
                       String meaningTr, String exampleDe, String exampleTr, Level level, boolean verified,
                       boolean isNew, int step, boolean askArticle) {
    }

    public record SessionView(UUID sessionId, List<Card> cards, int reviewCount, int newCount,
                              int dueTotal, int postponed, int dailyNewLimit, int dailyReviewLimit) {
    }

    /**
     * @param effectiveGrade artikel yanlissa "Bildim" -> "Zorlandim" (SPEC 8.1)
     */
    public record AnswerView(String wordId, Grade effectiveGrade, Boolean articleCorrect, String article,
                             int step, LocalDate due, int intervalDays, boolean strong) {
    }

    public record Stats(int studied, int strong, int dueToday, int reviews) {
    }

    private final VocabularyStore store;
    private final LearningAnswerStore answers;
    private final ContentCatalog catalog;
    private final SrsLadder ladder;
    private final OnboardingStore onboarding;
    private final LevelService levels;
    private final ActivityStore sessions;
    private final ActivityService activities;
    private final ActivityProperties activityConfig;

    public VocabularyService(VocabularyStore store, LearningAnswerStore answers, ContentCatalog catalog,
                             SrsProperties srs, OnboardingStore onboarding, LevelService levels,
                             ActivityStore sessions, ActivityService activities,
                             ActivityProperties activityConfig) {
        this.store = store;
        this.answers = answers;
        this.catalog = catalog;
        this.ladder = new SrsLadder(srs);
        this.onboarding = onboarding;
        this.levels = levels;
        this.sessions = sessions;
        this.activities = activities;
        this.activityConfig = activityConfig;
    }

    @Transactional
    public SessionView start(UUID userId, LocalDate today) {
        Plan plan = plan(userId, today);
        if (plan.size() == 0) {
            throw new NotFoundException("Bugün çalışılacak kelime yok; yarın yeni tekrarlar gelecek");
        }
        UUID sessionId = sessions.startSession(userId, "VOCABULARY");

        List<Card> cards = new ArrayList<>();
        for (DueCard c : plan.reviews()) {
            word(c.wordId()).ifPresent(w -> cards.add(card(w, false, c.step())));
        }
        for (String id : plan.newWords()) {
            word(id).ifPresent(w -> cards.add(card(w, true, 0)));
        }
        return new SessionView(sessionId, cards,
                (int) cards.stream().filter(c -> !c.isNew()).count(),
                (int) cards.stream().filter(Card::isNew).count(),
                plan.dueTotal(), plan.postponed(), dailyNew(userId), ladder.dailyReviewLimit());
    }

    /**
     * Bir kartin sonucu.
     *
     * @param articleGiven isimlerde kullanicinin sectigi artikel (null: sorulmadi)
     */
    @Transactional
    public AnswerView answer(UUID userId, UUID sessionId, String wordId, Grade grade, String articleGiven,
                             LocalDate today) {
        if (grade == null) {
            throw new ValidationException("Bilemedim / Zorlandım / Bildim seçeneklerinden birini işaretle");
        }
        Word word = word(wordId).orElseThrow(() -> new NotFoundException("Kelime bulunamadı"));
        if (sessionId != null && !answers.ownsSession(userId, sessionId)) {
            throw new ValidationException("Bu oturum sana ait değil");
        }

        Boolean articleCorrect = null;
        if (word.article() != null && articleGiven != null && !articleGiven.isBlank()) {
            articleCorrect = word.article().equalsIgnoreCase(articleGiven.trim());
            answers.insert(userId, sessionId, Kind.ARTICLE, wordId, word.article(), articleCorrect,
                    articleGiven.trim(), today);
        }

        // Kelime dogru ama artikel yanlissa "Zorlandim" sayilir (SPEC 8.1).
        Grade etkili = grade == Grade.BILDIM && Boolean.FALSE.equals(articleCorrect)
                ? Grade.ZORLANDIM
                : grade;
        answers.insert(userId, sessionId, Kind.WORD, wordId, null, etkili == Grade.BILDIM, null, today);

        Optional<Progress> mevcut = store.find(userId, wordId);
        Next next = mevcut.map(p -> ladder.apply(p.step(), etkili, today)).orElseGet(() -> ladder.first(today));
        if (mevcut.isPresent()) {
            store.update(userId, wordId, next.step(), next.due(), etkili, today);
        } else {
            store.insertNew(userId, wordId, next.step(), next.due(), etkili, today);
        }

        return new AnswerView(wordId, etkili, articleCorrect, word.article(), next.step(), next.due(),
                next.intervalDays(), ladder.strong(next.step()));
    }

    /** Oturumu bitirir ve calismayi otomatik kaydeder (SPEC 6.1). */
    @Transactional
    public void finish(UUID userId, UUID sessionId, List<Long> interactions, LocalDate today) {
        if (!answers.ownsSession(userId, sessionId)) {
            throw new NotFoundException("Oturum bulunamadı");
        }
        List<Instant> events = interactions == null ? List.of()
                : interactions.stream().sorted().map(Instant::ofEpochMilli).toList();
        long active = ActivityRules.activeSeconds(events, activityConfig.idleGapMinutes());
        sessions.finishSession(userId, sessionId, active);
        // Kelime calismasi ogrenme aktivitesidir: seviye kaniti uretmez (K2).
        activities.recordSite(userId, new SiteActivity(ActivityType.KELIME, today, active,
                null, null, null, null, null, sessionId));
    }

    @Transactional(readOnly = true)
    public Stats stats(UUID userId, LocalDate today) {
        int gucluBasamak = gucluBasamak();
        return new Stats(store.countStudied(userId), store.countAtLeastStep(userId, gucluBasamak),
                store.dueCount(userId, today), answers.total(userId, Kind.WORD).answers());
    }

    /** Koc icin: bugunku tekrar sayisi ve tahmini suresi. */
    @Transactional(readOnly = true)
    public int dueMinutes(UUID userId, LocalDate today) {
        int toplam = 0;
        for (String id : store.dueWordIds(userId, today)) {
            toplam += word(id).map(Word::estimatedMinutes).orElse(1);
        }
        return toplam;
    }

    // ------------------------------------------------------------------

    Plan plan(UUID userId, LocalDate today) {
        int yeniLimit = dailyNew(userId);
        Set<String> gorulen = store.seenWordIds(userId);
        List<String> adaylar = yeniAdaylar(userId, today, gorulen);
        return VocabularyPlanner.plan(store.cards(userId), adaylar, today, yeniLimit,
                ladder.dailyReviewLimit());
    }

    /** Calisma seviyesine kadar olan, henuz girilmemis kelimeler; kolaydan zora. */
    private List<String> yeniAdaylar(UUID userId, LocalDate today, Set<String> gorulen) {
        Level calisma = levels.overview(userId, today).working().level().forContent();
        List<String> out = new ArrayList<>();
        for (Level l : Level.assessable()) {
            if (!calisma.isAtLeast(l)) {
                continue;
            }
            catalog.words().values().stream()
                    .filter(w -> w.level() == l)
                    .map(Word::id)
                    .filter(id -> !gorulen.contains(id))
                    .forEach(out::add);
        }
        return out;
    }

    private int dailyNew(UUID userId) {
        int dakika = onboarding.rhythm(userId).map(OnboardingStore.Rhythm::dailyMinutes).orElse(15);
        return ladder.dailyNew(dakika);
    }

    private int gucluBasamak() {
        for (int step = 1; step <= ladder.son(); step++) {
            if (ladder.strong(step)) {
                return step;
            }
        }
        return ladder.son();
    }

    private Optional<Word> word(String id) {
        return Optional.ofNullable(catalog.words().get(id));
    }

    private static Card card(Word w, boolean isNew, int step) {
        return new Card(w.id(), w.lemma(), w.article(), w.plural(), w.partOfSpeech(), w.meaningTr(),
                w.exampleDe(), w.exampleTr(), w.level(), w.verified(), isNew, step, w.article() != null);
    }
}
