package com.ichsprechedeutsch.config;

import com.ichsprechedeutsch.common.model.Level;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Koc motoru ve plan (SPEC Bolum 5, Ek A). Ek A disindaki ayarlar K-017'de.
 */
@ConfigurationProperties(prefix = "coach")
public record CoachProperties(
        int maxRecommendations,
        int maxPerCategory,
        List<String> ruleOrder,
        Window grammar,
        Window article,
        int recurringErrorSessions,
        int errorResolvedStreak,
        int skillIdleDays,
        int comebackDays,
        List<Integer> grammarCheckDays,
        DayOfWeek weekStart,
        double comebackDayFraction,
        int comebackSpreadDays,
        int planBlockMinutes,
        int planMinItemMinutes,
        int recommendationShareBoost,
        double examPracticeFraction,
        Map<String, Map<String, Integer>> weeklyShares,
        Map<Level, String> sharesByLevel) {

    /** Bir calisma seviyesinin haftalik paylari (tur anahtari -> yuzde). */
    public Map<String, Integer> sharesFor(Level level) {
        return weeklyShares.get(sharesByLevel.get(level));
    }

    public record Window(int size, double threshold, int minAnswers) {
    }
}
