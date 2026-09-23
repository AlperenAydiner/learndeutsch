package com.ichsprechedeutsch.exam;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.Skill;
import com.ichsprechedeutsch.content.ContentCatalog;
import com.ichsprechedeutsch.content.ExamLink;
import com.ichsprechedeutsch.level.LevelService;
import com.ichsprechedeutsch.level.LevelService.Overview;
import com.ichsprechedeutsch.level.SkillEstimate;
import com.ichsprechedeutsch.onboarding.OnboardingStore;
import com.ichsprechedeutsch.skills.SkillPracticeService;
import com.ichsprechedeutsch.skills.SkillPracticeService.TaskView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sinav bolumu (SPEC 7). Kendi sinavimizi uretmiyoruz: hedef seviyedeki
 * site ici pratikleri bir arada gosterir, resmi kurumlarin kendi ornek
 * sinavlarina yonlendirir ve sonucun nasil girilecegini soyler.
 *
 * <p>Telifli sinav materyali kopyalanmaz (10.2). Disarida alinan sonuc
 * "Bugun ne yaptin?" formundan MODELLTEST/OFFICIAL_EXAM kaynagiyla
 * girilir; kademesi 4.4'e gore hesaplanir.
 */
@Service
public class ExamService {

    /**
     * @param ready      bu beceride hedef seviyeye ulasildi mi (4.8)
     * @param statusTr   beceri durumunun insan diliyle ozeti
     */
    public record SkillRow(Skill skill, String label, boolean ready, String statusTr, int evidenceCount,
                           List<TaskView> practice) {
    }

    public record View(Level target, Level working, boolean goalComplete, boolean nearGoal,
                       List<SkillRow> skills, List<ExamLink> links, String noteTr) {
    }

    private final ContentCatalog catalog;
    private final LevelService levels;
    private final OnboardingStore onboarding;
    private final SkillPracticeService practice;

    public ExamService(ContentCatalog catalog, LevelService levels, OnboardingStore onboarding,
                       SkillPracticeService practice) {
        this.catalog = catalog;
        this.levels = levels;
        this.onboarding = onboarding;
        this.practice = practice;
    }

    @Transactional(readOnly = true)
    public View view(UUID userId, LocalDate today) {
        Overview o = levels.overview(userId, today);
        Level target = onboarding.activeGoal(userId).map(OnboardingStore.Goal::target).orElse(null);
        Level sinav = target != null ? target : o.working().level().forContent();

        // Hedef seviyedeki pratik: calisma seviyesinin ustunde olsa da gosterilir.
        Map<Skill, List<TaskView>> pratik = new LinkedHashMap<>();
        for (Skill s : Skill.values()) {
            pratik.put(s, practice.atLevel(s, sinav));
        }

        List<SkillRow> satirlar = new ArrayList<>();
        for (Skill s : Skill.values()) {
            SkillEstimate e = o.skills().get(s);
            boolean hazir = o.goal() != null && o.goal().skills().stream()
                    .anyMatch(g -> g.skill() == s && g.ok());
            satirlar.add(new SkillRow(s, s.name(), hazir, durumMetni(e, sinav),
                    e == null ? 0 : e.evidenceCount(), pratik.get(s)));
        }

        return new View(sinav, o.working().level(), o.goal() != null && o.goal().complete(),
                o.goal() != null && o.goal().nearGoal(), satirlar, catalog.examLinks(sinav),
                "Buradaki pratikler site içi çalışmadır. Resmî seviyeni yalnız kurumların kendi "
                        + "sınavları belirler; deneme ya da resmî sınav sonucunu girersen tahminine "
                        + "daha güvenilir bir kanıt olarak katılır.");
    }

    /** Veri yoksa sayi uydurulmaz (K3). */
    private static String durumMetni(SkillEstimate e, Level hedef) {
        if (e == null || e.level() == null) {
            return "Bu beceride henüz kanıt yok.";
        }
        if (e.level().isAtLeast(hedef)) {
            return "Tahmin hedef seviyede ya da üstünde.";
        }
        return "Tahmin " + hedef + " seviyesinin altında.";
    }
}
