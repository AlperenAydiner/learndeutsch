package com.ichsprechedeutsch.level;

import com.ichsprechedeutsch.config.LevelProperties;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Yeniden degerlendirme onerisi (SPEC 4.7): son degerlendirmeden bu yana
 * belirli sure gectiyse ya da yeterli yeni kanit biriktiyse (hangisi once
 * gelirse) yerlestirme testi veya Kann-Beschreibungen onerilir.
 *
 * @param reason "DAYS" ya da "NEW_EVIDENCE"; due=false ise null
 */
public record Reassessment(boolean due, String reason, long daysSince, int newEvidence) {

    /** @param lastAssessment son yerlestirme ya da oz degerlendirme tarihi; hic yoksa null */
    public static Reassessment check(LocalDate lastAssessment, int newEvidenceSince,
                                     LocalDate today, LevelProperties config) {
        if (lastAssessment == null) {
            return new Reassessment(false, null, 0, newEvidenceSince);
        }
        long days = ChronoUnit.DAYS.between(lastAssessment, today);
        LevelProperties.Reassessment r = config.reassessment();
        if (days >= r.days()) {
            return new Reassessment(true, "DAYS", days, newEvidenceSince);
        }
        if (newEvidenceSince >= r.newEvidence()) {
            return new Reassessment(true, "NEW_EVIDENCE", days, newEvidenceSince);
        }
        return new Reassessment(false, null, days, newEvidenceSince);
    }
}
