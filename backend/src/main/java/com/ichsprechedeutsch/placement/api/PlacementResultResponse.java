package com.ichsprechedeutsch.placement.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlacementResultResponse(
        UUID attemptId,
        String estimatedLevel,
        int correct,
        int total,
        /** Beceri -> dogruluk orani (0..1) */
        Map<String, Double> skillScores,
        List<CategoryResult> categories,
        List<String> masteredCategoryCodes,
        /** Programdan atlanacak icerik birimi sayisi tahmini. */
        int skippableUnits) {

    public record CategoryResult(
            String code,
            String nameTr,
            int correct,
            int total,
            /** MASTERED | PARTIAL | WEAK */
            String status) {
    }
}
