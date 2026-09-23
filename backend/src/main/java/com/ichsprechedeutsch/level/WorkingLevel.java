package com.ichsprechedeutsch.level;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.StartMode;
import com.ichsprechedeutsch.config.LevelProperties;

/**
 * Calisma seviyesi (SPEC 4.6): yalniz hangi icerigin onerilecegini belirler.
 * Seviye iddiasi degildir; arayuzde "seviyen" diye gosterilmez.
 *
 * @param recommendPlacement "bir miktar biliyorum" deyip test yapmamis
 *                           kullaniciya koc yerlestirme testini onerir
 */
public record WorkingLevel(Level level, Source source, boolean recommendPlacement) {

    public enum Source { OVERALL_ESTIMATE, PLACEMENT, ONBOARDING, A0_EXIT }

    /**
     * Sira: (1) genel tahmin, (2) yerlestirme sonucu ("A1'in altinda" -> A0),
     * (3) onboarding cevabi. A0'dan cikis: A0 temel konulari tamamlandiysa A1.
     *
     * @param placement       en son yerlestirme sonucu; yoksa null (A0 = A1'in altinda)
     * @param startMode       onboarding cevabi; henuz yoksa null
     * @param a0CoreCompleted A0 temel konulari tamamlandi mi (Faz 3b'ye kadar false)
     */
    public static WorkingLevel resolve(OverallEstimate overall, Level placement, StartMode startMode,
                                       boolean a0CoreCompleted, LevelProperties config) {
        WorkingLevel base;
        if (overall != null && overall.sufficient()) {
            base = new WorkingLevel(overall.level(), Source.OVERALL_ESTIMATE, false);
        } else if (placement != null) {
            base = new WorkingLevel(placement, Source.PLACEMENT, false);
        } else if (startMode == StartMode.SOME_KNOWLEDGE) {
            base = new WorkingLevel(config.defaultWorkingLevelSomeKnowledge(), Source.ONBOARDING, true);
        } else {
            base = new WorkingLevel(Level.A0, Source.ONBOARDING, false);
        }

        if (base.level() == Level.A0 && a0CoreCompleted) {
            return new WorkingLevel(Level.A1, Source.A0_EXIT, false);
        }
        return base;
    }

    /** Bir beceri icin icerik secerken: o becerinin tahmini varsa o, yoksa genel. */
    public Level forSkill(SkillEstimate skill) {
        return skill != null && skill.hasData() ? skill.rank() : level;
    }
}
