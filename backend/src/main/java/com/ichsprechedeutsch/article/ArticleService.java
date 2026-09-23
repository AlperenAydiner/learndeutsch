package com.ichsprechedeutsch.article;

import com.ichsprechedeutsch.activity.ActivityRules;
import com.ichsprechedeutsch.activity.ActivityService;
import com.ichsprechedeutsch.activity.ActivityService.SiteActivity;
import com.ichsprechedeutsch.activity.ActivityStore;
import com.ichsprechedeutsch.activity.ActivityType;
import com.ichsprechedeutsch.article.ArticleDrill.History;
import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.config.ActivityProperties;
import com.ichsprechedeutsch.config.CoachProperties;
import com.ichsprechedeutsch.content.ContentCatalog;
import com.ichsprechedeutsch.content.Word;
import com.ichsprechedeutsch.learning.LearningAnswerStore;
import com.ichsprechedeutsch.learning.LearningAnswerStore.Kind;
import com.ichsprechedeutsch.learning.LearningAnswerStore.Window;
import com.ichsprechedeutsch.level.LevelService;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Artikel (der/die/das) alistirmasi (SPEC 7). Kelime verisini kullanir,
 * ayri artikel verisi tutulmaz. Kendi istatistigi vardir; seviye kaniti
 * uretmez (K2).
 */
@Service
public class ArticleService {

    public record Item(String wordId, String lemma, String meaningTr, String plural, String exampleDe,
                       String exampleTr, Level level, boolean verified) {
    }

    public record SessionView(UUID sessionId, List<Item> items) {
    }

    public record AnswerView(String wordId, boolean correct, String article, String exampleDe,
                             String exampleTr) {
    }

    /**
     * @param byArticle der/die/das icin son N yanit
     * @param total     tum zamanlarin ozeti
     */
    public record Stats(Map<String, Window> byArticle, Window total, int windowSize) {
    }

    private static final List<String> ARTIKELLER = List.of("der", "die", "das");

    private final ContentCatalog catalog;
    private final LearningAnswerStore answers;
    private final LevelService levels;
    private final ActivityStore sessions;
    private final ActivityService activities;
    private final ActivityProperties activityConfig;
    private final CoachProperties coachConfig;
    private final Random random = new SecureRandom();

    public ArticleService(ContentCatalog catalog, LearningAnswerStore answers, LevelService levels,
                          ActivityStore sessions, ActivityService activities,
                          ActivityProperties activityConfig, CoachProperties coachConfig) {
        this.catalog = catalog;
        this.answers = answers;
        this.levels = levels;
        this.sessions = sessions;
        this.activities = activities;
        this.activityConfig = activityConfig;
        this.coachConfig = coachConfig;
    }

    @Transactional
    public SessionView start(UUID userId, LocalDate today, int count) {
        Level calisma = levels.overview(userId, today).working().level().forContent();
        List<String> adaylar = catalog.words().values().stream()
                .filter(w -> w.article() != null)
                .filter(w -> calisma.isAtLeast(w.level()))
                .map(Word::id)
                .toList();
        if (adaylar.isEmpty()) {
            throw new NotFoundException("Bu seviyede artikel çalışılacak kelime yok");
        }

        Map<String, History> gecmis = new LinkedHashMap<>();
        answers.windowsByItem(userId, Kind.ARTICLE)
                .forEach((id, w) -> gecmis.put(id, new History(w.answers(), w.correct())));

        List<Item> items = ArticleDrill.pick(adaylar, gecmis, count, random).stream()
                .map(id -> catalog.words().get(id))
                .map(w -> new Item(w.id(), w.lemma(), w.meaningTr(), w.plural(), w.exampleDe(),
                        w.exampleTr(), w.level(), w.verified()))
                .toList();
        return new SessionView(sessions.startSession(userId, "ARTICLE"), items);
    }

    @Transactional
    public AnswerView answer(UUID userId, UUID sessionId, String wordId, String given, LocalDate today) {
        if (given == null || !ARTIKELLER.contains(given.trim().toLowerCase(java.util.Locale.ROOT))) {
            throw new ValidationException("der, die ya da das seç");
        }
        Word word = Optional.ofNullable(catalog.words().get(wordId))
                .orElseThrow(() -> new NotFoundException("Kelime bulunamadı"));
        if (word.article() == null) {
            throw new ValidationException("Bu kelime isim değil");
        }
        if (sessionId != null && !answers.ownsSession(userId, sessionId)) {
            throw new ValidationException("Bu oturum sana ait değil");
        }

        boolean dogru = word.article().equalsIgnoreCase(given.trim());
        answers.insert(userId, sessionId, Kind.ARTICLE, wordId, word.article(), dogru, given.trim(), today);
        return new AnswerView(wordId, dogru, word.article(), word.exampleDe(), word.exampleTr());
    }

    @Transactional
    public void finish(UUID userId, UUID sessionId, List<Long> interactions, LocalDate today) {
        if (!answers.ownsSession(userId, sessionId)) {
            throw new NotFoundException("Oturum bulunamadı");
        }
        List<Instant> events = interactions == null ? List.of()
                : interactions.stream().sorted().map(Instant::ofEpochMilli).toList();
        long active = ActivityRules.activeSeconds(events, activityConfig.idleGapMinutes());
        sessions.finishSession(userId, sessionId, active);
        activities.recordSite(userId, new SiteActivity(ActivityType.ARTIKEL, today, active,
                null, null, null, null, null, sessionId));
    }

    @Transactional(readOnly = true)
    public Stats stats(UUID userId) {
        int pencere = coachConfig.article().size();
        Map<String, Window> ham = answers.windowsByTag(userId, Kind.ARTICLE, pencere);
        Map<String, Window> out = new LinkedHashMap<>();
        for (String a : ARTIKELLER) {
            out.put(a, ham.getOrDefault(a, new Window(0, 0)));
        }
        return new Stats(out, answers.total(userId, Kind.ARTICLE), pencere);
    }

    /** Koc icin: der/die/das pencereleri. */
    @Transactional(readOnly = true)
    public Map<String, Window> windows(UUID userId) {
        return answers.windowsByTag(userId, Kind.ARTICLE, coachConfig.article().size());
    }

    /** Sitede artikel calisilabilecek kelime var mi (A0'da yok). */
    public boolean available(Level working) {
        List<String> adaylar = new ArrayList<>();
        catalog.words().values().stream()
                .filter(w -> w.article() != null && working.isAtLeast(w.level()))
                .forEach(w -> adaylar.add(w.id()));
        return !adaylar.isEmpty();
    }
}
