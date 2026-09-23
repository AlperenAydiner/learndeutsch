package com.ichsprechedeutsch.grammar;

import com.ichsprechedeutsch.activity.ActivityRules;
import com.ichsprechedeutsch.activity.ActivityService;
import com.ichsprechedeutsch.activity.ActivityService.SiteActivity;
import com.ichsprechedeutsch.activity.ActivityStore;
import com.ichsprechedeutsch.activity.ActivityType;
import com.ichsprechedeutsch.answer.AnswerCheck;
import com.ichsprechedeutsch.answer.AnswerCheck.Warning;
import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.config.ActivityProperties;
import com.ichsprechedeutsch.config.AnswerProperties;
import com.ichsprechedeutsch.config.CoachProperties;
import com.ichsprechedeutsch.content.ContentCatalog;
import com.ichsprechedeutsch.content.GrammarLesson;
import com.ichsprechedeutsch.content.GrammarTopic;
import com.ichsprechedeutsch.content.Question;
import com.ichsprechedeutsch.errors.ErrorMemory;
import com.ichsprechedeutsch.errors.ErrorMemory.State;
import com.ichsprechedeutsch.errors.ErrorStore;
import com.ichsprechedeutsch.grammar.GrammarStore.Progress;
import com.ichsprechedeutsch.learning.LearningAnswerStore;
import com.ichsprechedeutsch.learning.LearningAnswerStore.Kind;
import com.ichsprechedeutsch.level.LevelService;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gramer modulu (SPEC 8.2): kisa aciklama -> ornek -> mini soru ->
 * cevap + hata aciklamasi -> kontrollu uretim. Uzun ders sayfasi yok.
 *
 * <p>Ogrenme aktivitesidir: seviye kaniti uretmez (K2, 8.5). Yanlis
 * cevaplar hata hafizasina etiketle yazilir (8.4).
 */
@Service
public class GrammarService {

    /** Konu listesi satiri. */
    public record TopicView(String id, String titleTr, Level level, boolean core, int estimatedMinutes,
                            boolean hasLesson, boolean verified, int answers, int correct,
                            LocalDate completedOn, boolean dueCheck, LocalDate nextCheck) {
    }

    /** Ders adimi: mini soru ya da kontrollu uretim. Dogru cevap istemciye gitmez. */
    public record Step(String id, String kind, String type, String promptTr, String prompt,
                       List<String> options, List<String> parts) {
    }

    public record Example(String de, String tr) {
    }

    public record LessonView(UUID sessionId, String topicId, String titleTr, Level level,
                             String explanationTr, String turkishNoteTr, List<Example> examples,
                             List<Step> steps, boolean verified, boolean checkRound) {
    }

    /**
     * @param warnings yazim uyarilari (buyuk/kucuk harf, umlaut) — dogru sayildi ama not dusuldu
     */
    public record AnswerView(String itemId, boolean correct, String answer, String explanationTr,
                             List<Warning> warnings) {
    }

    public record Stats(int studiedTopics, int completedTopics, int dueChecks, int activeErrors,
                        int resolvedErrors) {
    }

    /** Hata hafizasi satiri (Koclukta gosterilir). */
    public record ErrorView(String tag, String titleTr, String status, int occurrences,
                            int distinctSessions, LocalDate lastSeen, boolean recurring) {
    }

    static final String TAG_YAZIM_HARF = "YAZIM_BUYUK_KUCUK";
    static final String TAG_YAZIM_UMLAUT = "YAZIM_UMLAUT";

    private final ContentCatalog catalog;
    private final GrammarStore store;
    private final ErrorStore errors;
    private final ErrorMemory memory;
    private final LearningAnswerStore answers;
    private final LevelService levels;
    private final ActivityStore sessions;
    private final ActivityService activities;
    private final ActivityProperties activityConfig;
    private final AnswerProperties answerConfig;
    private final CoachProperties coachConfig;
    private final Random random = new SecureRandom();

    public GrammarService(ContentCatalog catalog, GrammarStore store, ErrorStore errors,
                          LearningAnswerStore answers, LevelService levels, ActivityStore sessions,
                          ActivityService activities, ActivityProperties activityConfig,
                          AnswerProperties answerConfig, CoachProperties coachConfig) {
        this.catalog = catalog;
        this.store = store;
        this.errors = errors;
        this.memory = new ErrorMemory(coachConfig);
        this.answers = answers;
        this.levels = levels;
        this.sessions = sessions;
        this.activities = activities;
        this.activityConfig = activityConfig;
        this.answerConfig = answerConfig;
        this.coachConfig = coachConfig;
    }

    /** Calisma seviyesine kadar olan gramer konulari, ilerlemeyle birlikte. */
    @Transactional(readOnly = true)
    public List<TopicView> topics(UUID userId, LocalDate today) {
        Level working = levels.overview(userId, today).working().level().forContent();
        List<Progress> progress = store.all(userId);
        List<TopicView> out = new ArrayList<>();
        for (GrammarTopic t : catalog.topics().values()) {
            if (!"GRAMMAR".equals(t.kind()) || t.level() == null || !working.isAtLeast(t.level())) {
                continue;
            }
            Progress p = progress.stream().filter(x -> x.topicId().equals(t.id())).findFirst().orElse(null);
            out.add(new TopicView(t.id(), t.titleTr(), t.level(), t.core(), t.estimatedMinutes(),
                    catalog.lesson(t.id()).isPresent(), t.verified(),
                    p == null ? 0 : p.answers(), p == null ? 0 : p.correct(),
                    p == null ? null : p.completedOn(),
                    p != null && GrammarCheck.due(p.nextCheck(), today),
                    p == null ? null : p.nextCheck()));
        }
        return out;
    }

    /** Konunun ders dongusunu baslatir. */
    @Transactional
    public LessonView start(UUID userId, String topicId, LocalDate today) {
        GrammarTopic topic = catalog.topics().get(topicId);
        if (topic == null || !"GRAMMAR".equals(topic.kind())) {
            throw new NotFoundException("Gramer konusu bulunamadı");
        }
        GrammarLesson lesson = catalog.lesson(topicId)
                .orElseThrow(() -> new NotFoundException("Bu konunun ders içeriği henüz hazır değil"));

        List<Step> steps = new ArrayList<>();
        for (Question q : catalog.questionsForTopic(topicId)) {
            steps.add(new Step(q.id(), "QUESTION", q.type(), null, q.prompt(),
                    q.options() == null || q.options().isEmpty() ? null : q.options(), null));
        }
        for (GrammarLesson.Exercise e : lesson.production()) {
            List<String> parts = null;
            if (e.parts() != null && !e.parts().isEmpty()) {
                parts = new ArrayList<>(e.parts());
                Collections.shuffle(parts, random);
            }
            steps.add(new Step(e.id(), "PRODUCTION", e.type(), e.promptTr(), e.prompt(), null, parts));
        }

        Progress p = store.find(userId, topicId).orElse(null);
        boolean kontrol = p != null && GrammarCheck.due(p.nextCheck(), today);
        List<Example> ornekler = lesson.examples().stream()
                .map(x -> new Example(x.de(), x.tr()))
                .toList();

        return new LessonView(sessions.startSession(userId, "GRAMMAR"), topicId, topic.titleTr(),
                topic.level(), lesson.explanationTr(), lesson.turkishNoteTr(), ornekler, steps,
                lesson.verified() && topic.verified(), kontrol);
    }

    /** Bir mini sorunun ya da uretim alistirmasinin cevabi. */
    @Transactional
    public AnswerView answer(UUID userId, UUID sessionId, String topicId, String itemId, String given,
                             LocalDate today) {
        if (catalog.topics().get(topicId) == null) {
            throw new NotFoundException("Gramer konusu bulunamadı");
        }
        if (sessionId != null && !answers.ownsSession(userId, sessionId)) {
            throw new ValidationException("Bu oturum sana ait değil");
        }

        String dogru;
        List<String> kabul;
        String aciklama;
        Optional<Question> soru = catalog.question(itemId);
        if (soru.isPresent()) {
            dogru = soru.get().answer();
            kabul = soru.get().acceptedAnswers();
            aciklama = soru.get().explanationTr();
        } else {
            GrammarLesson.Exercise e = catalog.lesson(topicId)
                    .flatMap(l -> l.production().stream().filter(x -> x.id().equals(itemId)).findFirst())
                    .orElseThrow(() -> new NotFoundException("Alıştırma bulunamadı"));
            dogru = e.answer();
            kabul = e.acceptedAnswers();
            aciklama = e.explanationTr();
        }

        AnswerCheck.Result sonuc = AnswerCheck.check(given, dogru, kabul, answerConfig);
        answers.insert(userId, sessionId, Kind.GRAMMAR, itemId, topicId, sonuc.correct(), given, today);
        store.recordAnswer(userId, topicId, sonuc.correct(), today);

        // Hata hafizasi: konu etiketi her cevapta, yazim etiketleri yalniz uyari varken.
        etiketiGuncelle(userId, sessionId, topicId, sonuc.correct(), today);
        for (Warning w : sonuc.warnings()) {
            etiketiGuncelle(userId, sessionId, yazimEtiketi(w), false, today);
        }

        return new AnswerView(itemId, sonuc.correct(), dogru, aciklama, sonuc.warnings());
    }

    /**
     * Konu dongusu tamamlandi: kontrol tekrarlari planlanir (SPEC 8.2).
     * Hic cevap verilmemisse tamamlama yazilmaz (K3).
     */
    @Transactional
    public void complete(UUID userId, String topicId, LocalDate today) {
        Progress p = store.find(userId, topicId)
                .orElseThrow(() -> new NotFoundException("Bu konuda henüz çalışma yok"));
        if (p.answers() == 0) {
            throw new ValidationException("Önce konunun sorularını cevapla");
        }
        List<Integer> araliklar = coachConfig.grammarCheckDays();
        if (p.completedOn() != null && GrammarCheck.due(p.nextCheck(), today)) {
            // Kontrol tekrari yapildi: siradaki kontrole gec.
            int sonraki = p.checkIndex() + 1;
            store.advanceCheck(userId, topicId, sonraki,
                    GrammarCheck.next(p.completedOn(), sonraki, araliklar).orElse(null));
            return;
        }
        store.complete(userId, topicId, today, GrammarCheck.next(today, 0, araliklar).orElse(null));
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
        activities.recordSite(userId, new SiteActivity(ActivityType.GRAMER, today, active,
                null, null, null, null, null, sessionId));
    }

    @Transactional(readOnly = true)
    public Stats stats(UUID userId, LocalDate today) {
        List<Progress> hepsi = store.all(userId);
        long tamamlanan = hepsi.stream().filter(p -> p.completedOn() != null).count();
        List<State> hatalar = errors.all(userId);
        long aktif = hatalar.stream().filter(h -> h.status() != ErrorMemory.Status.RESOLVED).count();
        long cozulen = hatalar.size() - aktif;
        return new Stats(hepsi.size(), (int) tamamlanan, store.dueCheckTopics(userId, today).size(),
                (int) aktif, (int) cozulen);
    }

    /** Hata hafizasi listesi (Kocluk: "tekrarlayan hatalar"). */
    @Transactional(readOnly = true)
    public List<ErrorView> errorList(UUID userId) {
        return errors.all(userId).stream()
                .map(s -> new ErrorView(s.tag(), etiketAdi(s.tag()), s.status().name(), s.occurrences(),
                        s.distinctSessions(), s.lastSeen(), memory.recurring(s)))
                .toList();
    }

    /** Koc icin: cozulmemis etiketler. */
    @Transactional(readOnly = true)
    public List<State> unresolvedErrors(UUID userId) {
        return errors.unresolved(userId);
    }

    /** Koc icin: kontrol tekrari bekleyen konu sayisi. */
    @Transactional(readOnly = true)
    public int dueChecks(UUID userId, LocalDate today) {
        return store.dueCheckTopics(userId, today).size();
    }

    /** Etiketin kullaniciya gosterilecek adi. */
    public String etiketAdi(String tag) {
        if (TAG_YAZIM_HARF.equals(tag)) {
            return "Yazım: büyük/küçük harf";
        }
        if (TAG_YAZIM_UMLAUT.equals(tag)) {
            return "Yazım: ä/ö/ü/ß";
        }
        GrammarTopic t = catalog.topics().get(tag);
        return t == null ? tag : t.titleTr();
    }

    // ------------------------------------------------------------------

    private void etiketiGuncelle(UUID userId, UUID sessionId, String tag, boolean correct, LocalDate today) {
        ErrorStore.Stored mevcut = errors.find(userId, tag).orElse(null);
        if (mevcut == null && correct) {
            // Hic hata yapilmamis etiket icin kayit acmaya gerek yok.
            return;
        }
        State onceki = mevcut == null ? State.first(tag, today) : mevcut.state();
        boolean yeniOturum = mevcut == null || mevcut.lastSessionId() == null
                || !mevcut.lastSessionId().equals(sessionId);
        State yeni = memory.onAnswer(onceki, correct, yeniOturum, today);
        errors.save(userId, yeni, correct ? (mevcut == null ? null : mevcut.lastSessionId()) : sessionId);
    }

    private static String yazimEtiketi(Warning w) {
        return w == Warning.BUYUK_KUCUK_HARF ? TAG_YAZIM_HARF : TAG_YAZIM_UMLAUT;
    }
}
