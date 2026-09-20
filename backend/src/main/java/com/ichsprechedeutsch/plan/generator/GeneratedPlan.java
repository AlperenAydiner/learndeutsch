package com.ichsprechedeutsch.plan.generator;

import java.util.List;

/**
 * Ureticinin ciktisi. Henuz veritabanina yazilmamis bir plan onerisi.
 *
 * @param notes kullaniciya gosterilecek aciklamalar (atlanan konular,
 *              sigmayan birimler, sikisiklik uyarisi)
 */
public record GeneratedPlan(
        FeasibilityChecker.Verdict verdict,
        List<PlannedDay> days,
        int skippedUnits,
        int shortenedUnits,
        int unplacedUnits,
        List<String> notes) {
}
