package com.ichsprechedeutsch.skills;

import com.ichsprechedeutsch.activity.ActivityRules;
import com.ichsprechedeutsch.activity.ActivityService;
import com.ichsprechedeutsch.activity.ActivityService.SiteActivity;
import com.ichsprechedeutsch.activity.ActivityStore;
import com.ichsprechedeutsch.activity.ActivityType;
import com.ichsprechedeutsch.answer.AnswerCheck;
import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.Skill;
import com.ichsprechedeutsch.config.ActivityProperties;
import com.ichsprechedeutsch.config.AnswerProperties;
import com.ichsprechedeutsch.config.LevelProperties;
import com.ichsprechedeutsch.config.LevelProperties.CanDoAnswer;
import com.ichsprechedeutsch.content.CanDo;
import com.ichsprechedeutsch.content.ContentCatalog;
import com.ichsprechedeutsch.content.ListeningItem;
import com.ichsprechedeutsch.content.ReadingText;
import com.ichsprechedeutsch.content.SpeakingTask;
import com.ichsprechedeutsch.content.WritingTask;
import com.ichsprechedeutsch.learning.LearningAnswerStore;
import com.ichsprechedeutsch.level.LevelService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dort becerinin site ici calismasi (SPEC 7, 8.3).
 *
 * <p>Kanit kurallari 4.2'den gelir ve burada degistirilmez:
 * <ul>
 *   <li><b>Lesen:</b> otomatik puanlanir, kanit uretir.</li>
 *   <li><b>Schreiben:</b> kullanicinin rubrikle kendi degerlendirmesi kanit uretir.</li>
 *   <li><b>Sprechen:</b> Kann-Beschreibung oz degerlendirmesi kanit uretir.</li>
 *   <li><b>Hören:</b> site ici alistirmalar sentetik sesle calistigi icin
 *       KANIT URETMEZ; yalniz calisma gecmisine yazilir.</li>
 * </ul>
 * Icerik {@code verified:false} oldugu surece kanit bir kademe dusuk sayilir (K-008).
 */
@Service
public class SkillPracticeService {

    /** Liste satiri: hangi calisma, ne kadar surer, daha once yapildi mi. */
    public record TaskView(String id, Level level, String titleTr, String subtitleTr, int estimatedMinutes,
                           boolean verified, int timesDone) {
    }

    /** Istemciye giden soru: dogru cevap YOK. */
    public record QuestionView(String id, String type, String prompt, List<String> options) {
    }

    public record ReadingView(UUID sessionId, String id, Level level, String titleTr, String text,
                              List<ReadingText.Gloss> glossary, List<QuestionView> questions,
                              boolean verified) {
    }

    /**
     * @param text dikte metni: tarayici Web Speech ile okur. Ekranda gizlidir.
     */
    public record ListeningView(UUID sessionId, String id, Level level, String kind, String titleTr,
                                String text, List<QuestionView> questions, boolean verified) {
    }

    public record WritingView(UUID sessionId, String id, Level level, String titleTr, String taskTr,
                              String taskDe, int minWords, List<String> phrases, List<String> criteria,
                              int maxPerCriterion, boolean verified) {
    }

    public record SpeakingView(UUID sessionId, String id, Level level, String titleTr, String taskTr,
                               List<String> promptsDe, List<String> phrases, List<CanDo> canDos,
                               boolean verified) {
    }

    /** Soru soru inceleme (dogru cevap ancak gonderimden sonra gelir). */
    public record Review(String id, String prompt, String yourAnswer, String correctAnswer,
                         boolean correct, String explanationTr) {
    }

    /**
     * @param evidence kanit yazildi mi; yazilmadiysa {@code reasonTr} nedenini soyler (K3)
     */
    public record ResultView(double score, double maxScore, double ratio, List<Review> review,
                             boolean evidence, String reasonTr, String sampleAnswer,
                             DictationCheck.Result dictation) {
    }

    private final ContentCatalog catalog;
    private final LevelService levels;
    private final ActivityStore sessions;
    private final ActivityService activities;
    private final ActivityProperties activityConfig;
    private final AnswerProperties answerConfig;
    private final LevelProperties levelConfig;
    private final LearningAnswerStore answers;

    public SkillPracticeService(ContentCatalog catalog, LevelService levels, ActivityStore sessions,
                                ActivityService activities, ActivityProperties activityConfig,
                                AnswerProperties answerConfig, LevelProperties levelConfig,
                                LearningAnswerStore answers) {
        this.catalog = catalog;
        this.levels = levels;
        this.sessions = sessions;
        this.activities = activities;
        this.activityConfig = activityConfig;
        this.answerConfig = answerConfig;
        this.levelConfig = levelConfig;
        this.answers = answers;
    }

    // --- Listeler --------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TaskView> readingList(UUID userId, LocalDate today) {
        Level working = working(userId, today);
        return catalog.readings().values().stream()
                .filter(r -> working.isAtLeast(r.level()))
                .map(r -> new TaskView(r.id(), r.level(), r.titleTr(), r.topicTr(), r.estimatedMinutes(),
                        r.verified(), 0))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskView> listeningList(UUID userId, LocalDate today) {
        Level working = working(userId, today);
        return catalog.listenings().values().stream()
                .filter(l -> working.isAtLeast(l.level()))
                .map(l -> new TaskView(l.id(), l.level(), l.titleTr(),
                        "DICTATION".equals(l.kind()) ? "Dikte" : "Dinleyip anlama",
                        l.estimatedMinutes(), l.verified(), 0))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskView> writingList(UUID userId, LocalDate today) {
        Level working = working(userId, today);
        return catalog.writings().values().stream()
                .filter(w -> working.isAtLeast(w.level()))
                .map(w -> new TaskView(w.id(), w.level(), w.titleTr(), "En az " + w.minWords() + " kelime",
                        w.estimatedMinutes(), w.verified(), 0))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskView> speakingList(UUID userId, LocalDate today) {
        Level working = working(userId, today);
        return catalog.speakings().values().stream()
                .filter(sp -> working.isAtLeast(sp.level()))
                .map(sp -> new TaskView(sp.id(), sp.level(), sp.titleTr(), "Kendini kaydet ve dinle",
                        sp.estimatedMinutes(), sp.verified(), 0))
                .toList();
    }

    /**
     * Sinav hazirligi icin bir becerinin TAM OLARAK verilen seviyedeki
     * gorevleri. Calisma seviyesiyle suzulmez: B1 hedefli A2 kullanici
     * B1 pratigini gorebilmeli (SPEC 7, Sinav).
     */
    public List<TaskView> atLevel(Skill skill, Level level) {
        return switch (skill) {
            case LESEN -> catalog.readings().values().stream().filter(r -> r.level() == level)
                    .map(r -> new TaskView(r.id(), r.level(), r.titleTr(), r.topicTr(), r.estimatedMinutes(),
                            r.verified(), 0)).toList();
            case HOEREN -> catalog.listenings().values().stream().filter(l -> l.level() == level)
                    .map(l -> new TaskView(l.id(), l.level(), l.titleTr(),
                            "DICTATION".equals(l.kind()) ? "Dikte" : "Dinleyip anlama",
                            l.estimatedMinutes(), l.verified(), 0)).toList();
            case SCHREIBEN -> catalog.writings().values().stream().filter(w -> w.level() == level)
                    .map(w -> new TaskView(w.id(), w.level(), w.titleTr(), "En az " + w.minWords() + " kelime",
                            w.estimatedMinutes(), w.verified(), 0)).toList();
            case SPRECHEN -> catalog.speakings().values().stream().filter(sp -> sp.level() == level)
                    .map(sp -> new TaskView(sp.id(), sp.level(), sp.titleTr(), "Kendini kaydet ve dinle",
                            sp.estimatedMinutes(), sp.verified(), 0)).toList();
        };
    }

    // --- Oturum baslatma -------------------------------------------------

    @Transactional
    public ReadingView startReading(UUID userId, String id) {
        ReadingText r = catalog.readings().get(id);
        if (r == null) {
            throw new NotFoundException("Okuma metni bulunamadı");
        }
        return new ReadingView(sessions.startSession(userId, "READING"), r.id(), r.level(), r.titleTr(),
                r.text(), r.glossary(), sorular(r.questions()), r.verified());
    }

    @Transactional
    public ListeningView startListening(UUID userId, String id) {
        ListeningItem l = catalog.listenings().get(id);
        if (l == null) {
            throw new NotFoundException("Dinleme alıştırması bulunamadı");
        }
        return new ListeningView(sessions.startSession(userId, "LISTENING"), l.id(), l.level(), l.kind(),
                l.titleTr(), l.text(), sorular(l.questions()), l.verified());
    }

    @Transactional
    public WritingView startWriting(UUID userId, String id) {
        WritingTask w = catalog.writings().get(id);
        if (w == null) {
            throw new NotFoundException("Yazma görevi bulunamadı");
        }
        return new WritingView(sessions.startSession(userId, "WRITING"), w.id(), w.level(), w.titleTr(),
                w.taskTr(), w.taskDe(), w.minWords(), w.phrases(),
                WritingRubric.criteria(levelConfig), levelConfig.schreibenRubric().maxPerCriterion(),
                w.verified());
    }

    @Transactional
    public SpeakingView startSpeaking(UUID userId, String id) {
        SpeakingTask sp = catalog.speakings().get(id);
        if (sp == null) {
            throw new NotFoundException("Konuşma görevi bulunamadı");
        }
        return new SpeakingView(sessions.startSession(userId, "SPEAKING"), sp.id(), sp.level(), sp.titleTr(),
                sp.taskTr(), sp.promptsDe(), sp.phrases(), catalog.canDos(Skill.SPRECHEN, sp.level()),
                sp.verified());
    }

    // --- Gonderim --------------------------------------------------------

    /** Lesen: otomatik puanlanir ve kanit uretir (4.2). */
    @Transactional
    public ResultView submitReading(UUID userId, String id, UUID sessionId, Map<String, String> given,
                                    List<Long> interactions, LocalDate today) {
        ReadingText r = catalog.readings().get(id);
        if (r == null) {
            throw new NotFoundException("Okuma metni bulunamadı");
        }
        oturumKontrol(userId, sessionId);

        List<Review> review = new ArrayList<>();
        int dogru = degerlendir(r.questions(), given, review);
        double oran = r.questions().isEmpty() ? 0 : (double) dogru / r.questions().size();

        long aktif = bitir(userId, sessionId, interactions);
        activities.recordSite(userId, new SiteActivity(ActivityType.LESEN, today, aktif, r.level(),
                (double) dogru, (double) r.questions().size(), r.id(), r.verified(), sessionId));

        return new ResultView(dogru, r.questions().size(), oran, review, true,
                kanitNotu(r.verified()), null, null);
    }

    /**
     * Hören: sonuc KANIT DEGILDIR (4.2). Dikte kelime bazli karsilastirilir,
     * anlama alistirmasi otomatik puanlanir; ikisi de yalniz calisma
     * gecmisine yazilir.
     */
    @Transactional
    public ResultView submitListening(UUID userId, String id, UUID sessionId, String dictation,
                                      Map<String, String> given, List<Long> interactions, LocalDate today) {
        ListeningItem l = catalog.listenings().get(id);
        if (l == null) {
            throw new NotFoundException("Dinleme alıştırması bulunamadı");
        }
        oturumKontrol(userId, sessionId);

        List<Review> review = new ArrayList<>();
        DictationCheck.Result dikte = null;
        double puan;
        double azami;
        if ("DICTATION".equals(l.kind())) {
            dikte = DictationCheck.check(l.text(), dictation);
            puan = dikte.correct();
            azami = dikte.total();
        } else {
            int dogru = degerlendir(l.questions(), given, review);
            puan = dogru;
            azami = l.questions().size();
        }

        long aktif = bitir(userId, sessionId, interactions);
        // Puan gecmise yazilmaz: site ici Hören kanit uretmemeli (4.2).
        activities.recordSite(userId, new SiteActivity(ActivityType.HOEREN, today, aktif, null,
                null, null, l.id(), l.verified(), sessionId));

        return new ResultView(puan, azami, azami == 0 ? 0 : puan / azami, review, false,
                "Site içi dinleme alıştırmaları sentetik sesle çalıştığı için seviye kanıtı sayılmaz; "
                        + "dışarıdaki bir dinleme sınavının sonucunu girersen o kanıt olur.",
                l.textTr(), dikte);
    }

    /** Schreiben: kullanicinin rubrikle kendi degerlendirmesi kanit uretir (4.2). */
    @Transactional
    public ResultView submitWriting(UUID userId, String id, UUID sessionId, String text,
                                    Map<String, Integer> rubric, List<Long> interactions, LocalDate today) {
        WritingTask w = catalog.writings().get(id);
        if (w == null) {
            throw new NotFoundException("Yazma görevi bulunamadı");
        }
        oturumKontrol(userId, sessionId);
        int kelime = text == null || text.isBlank() ? 0 : AnswerCheck.sadelestir(text).split(" ").length;
        if (kelime < w.minWords()) {
            throw new ValidationException("Metin en az " + w.minWords() + " kelime olmalı (şu an " + kelime + ")");
        }
        WritingRubric.Result sonuc = WritingRubric.score(rubric, levelConfig);

        long aktif = bitir(userId, sessionId, interactions);
        activities.recordSite(userId, new SiteActivity(ActivityType.SCHREIBEN, today, aktif, w.level(),
                sonuc.score(), sonuc.maxScore(), w.id(), w.verified(), sessionId));

        return new ResultView(sonuc.score(), sonuc.maxScore(), sonuc.ratio(), List.of(), true,
                "Bu bir öz değerlendirmedir; kendi puanın düşük güvenli kanıt sayılır. " + kanitNotu(w.verified()),
                w.sampleAnswer(), null);
    }

    /** Sprechen: Kann-Beschreibung oz degerlendirmesi kanit uretir (4.2). */
    @Transactional
    public ResultView submitSpeaking(UUID userId, String id, UUID sessionId,
                                     Map<String, CanDoAnswer> canDo, List<Long> interactions,
                                     LocalDate today) {
        SpeakingTask sp = catalog.speakings().get(id);
        if (sp == null) {
            throw new NotFoundException("Konuşma görevi bulunamadı");
        }
        oturumKontrol(userId, sessionId);
        List<CanDo> beklenen = catalog.canDos(Skill.SPRECHEN, sp.level());
        if (canDo == null || canDo.size() != beklenen.size()) {
            throw new ValidationException("Her ifade için evet / kısmen / hayır işaretle");
        }
        for (CanDo c : beklenen) {
            if (!canDo.containsKey(c.id())) {
                throw new ValidationException("Eksik işaretleme var");
            }
        }
        CanDoScore.Result sonuc = CanDoScore.of(canDo, levelConfig);

        long aktif = bitir(userId, sessionId, interactions);
        activities.recordSite(userId, new SiteActivity(ActivityType.SPRECHEN, today, aktif, sp.level(),
                sonuc.score(), sonuc.maxScore(), sp.id(), sp.verified(), sessionId));

        return new ResultView(sonuc.score(), sonuc.maxScore(), sonuc.ratio(), List.of(), true,
                "Site konuşmanı dinlemez; bu bir öz değerlendirmedir ve düşük güvenli kanıt sayılır (K4). "
                        + kanitNotu(sp.verified()),
                null, null);
    }

    // --- Yardimcilar -----------------------------------------------------

    private Level working(UUID userId, LocalDate today) {
        return levels.overview(userId, today).working().level().forContent();
    }

    private void oturumKontrol(UUID userId, UUID sessionId) {
        if (sessionId != null && !answers.ownsSession(userId, sessionId)) {
            throw new ValidationException("Bu oturum sana ait değil");
        }
    }

    private long bitir(UUID userId, UUID sessionId, List<Long> interactions) {
        List<Instant> events = interactions == null ? List.of()
                : interactions.stream().sorted().map(Instant::ofEpochMilli).toList();
        long aktif = ActivityRules.activeSeconds(events, activityConfig.idleGapMinutes());
        if (sessionId != null) {
            sessions.finishSession(userId, sessionId, aktif);
        }
        return aktif;
    }

    private int degerlendir(List<ReadingText.Item> sorular, Map<String, String> given, List<Review> review) {
        Map<String, String> cevaplar = given == null ? Map.of() : given;
        int dogru = 0;
        for (ReadingText.Item q : sorular) {
            String cevap = cevaplar.get(q.id());
            boolean ok = AnswerCheck.check(cevap, q.answer(), q.acceptedAnswers(), answerConfig).correct();
            if (ok) {
                dogru++;
            }
            review.add(new Review(q.id(), q.prompt(), cevap, q.answer(), ok, q.explanationTr()));
        }
        return dogru;
    }

    private List<QuestionView> sorular(List<ReadingText.Item> sorular) {
        if (sorular == null) {
            return List.of();
        }
        List<QuestionView> out = new ArrayList<>();
        for (ReadingText.Item q : sorular) {
            out.add(new QuestionView(q.id(), q.type(),
                    q.prompt(), q.options() == null || q.options().isEmpty() ? null : q.options()));
        }
        return out;
    }

    private static String kanitNotu(boolean verified) {
        return verified
                ? "Sonuç Lesen/Schreiben kanıtı olarak kaydedildi."
                : "İçerik henüz kontrol edilmediği için bu kanıt bir kademe düşük sayılır.";
    }

    /** Ek A'daki rubrik olcutleri (arayuz icin). */
    public Map<String, Integer> rubricCriteria() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String c : WritingRubric.criteria(levelConfig)) {
            out.put(c, levelConfig.schreibenRubric().maxPerCriterion());
        }
        return out;
    }
}
