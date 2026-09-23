package com.ichsprechedeutsch.config;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.ResultSource;
import com.ichsprechedeutsch.common.model.Tier;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Seviye modeli esikleri (SPEC Bolum 4, Ek A). Degerler application.yml'de
 * durur; kodda sabit esik yoktur (K7).
 */
@ConfigurationProperties(prefix = "level")
public record LevelProperties(
        /** Kanitin kendi seviyesini desteklemesi icin puan orani esigi. */
        double passThreshold,
        Map<Tier, Double> tierWeights,
        /** verified:false icerikten gelen kanit bir kademe dusuk sayilsin mi. */
        boolean unverifiedDowngrade,
        int maxEvidenceAgeMonths,
        Reliable reliable,
        Overall overall,
        Map<CanDoAnswer, Double> canDoScores,
        Rubric schreibenRubric,
        Level defaultWorkingLevelSomeKnowledge,
        Reassessment reassessment,
        GoalCompletion goalCompletion,
        int nearGoalSkills,
        Map<ResultSource, Tier> sourceTiers,
        /** Kendi puanlanan Schreiben/Sprechen modelltesti (SPEC 4.4: dusuk). */
        Tier selfScoredProductiveModelltestTier) {

    public enum CanDoAnswer { YES, PARTIAL, NO }

    public enum OverallMethod { MEDIAN, WEAKEST }

    public enum MinConfidence { ESTIMATED, RELIABLE }

    public record Reliable(int minPositive, int recentDays, boolean requireMediumOrHigh) {
    }

    public record Overall(OverallMethod method, int minSkills) {
    }

    public record Rubric(int criteria, int maxPerCriterion, List<String> criteriaNames) {
    }

    public record Reassessment(int days, int newEvidence) {
    }

    public record GoalCompletion(int minPositive, int recentDays,
                                 MinConfidence minConfidence, boolean requireCoreGrammar) {
    }

    public double weight(Tier tier) {
        return tierWeights.get(tier);
    }
}
