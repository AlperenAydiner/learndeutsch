package com.ichsprechedeutsch.level;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.Skill;
import com.ichsprechedeutsch.config.LevelProperties;
import com.ichsprechedeutsch.config.LevelProperties.MinConfidence;
import com.ichsprechedeutsch.level.SkillEstimate.Confidence;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Hedef tamamlama (SPEC 4.8). Hedefe ulasilinca sistem kullaniciyi ust
 * seviyeye itmez (K6); bu sinif yalniz durumu hesaplar.
 *
 * @param skills her beceri icin ayri durum; ekran guven dokumunu buradan gosterir
 */
public record GoalCompletion(boolean complete, boolean nearGoal, List<SkillStatus> skills,
                             boolean coreGrammarRequired, boolean coreGrammarDone) {

    /**
     * @param estimateReached tahmin >= hedef
     * @param supporting      hedef seviyeyi destekleyen pozitif kanit sayisi (pencere icinde)
     * @param hasRecent       bunlardan en az biri guncel mi
     */
    public record SkillStatus(Skill skill, boolean estimateReached, int supporting, boolean hasRecent,
                              Confidence confidence, boolean ok) {
    }

    public static GoalCompletion evaluate(Level target, Map<Skill, SkillEstimate> estimates,
                                          Collection<Evidence> evidence, boolean coreGrammarDone,
                                          LocalDate today, LevelProperties config) {
        TierPolicy tiers = new TierPolicy(config);
        LevelProperties.GoalCompletion rule = config.goalCompletion();
        LocalDate recent = today.minusDays(rule.recentDays());
        LocalDate oldest = today.minusMonths(config.maxEvidenceAgeMonths());

        List<SkillStatus> statuses = new ArrayList<>();
        int reachedCount = 0;
        boolean allOk = true;
        for (Skill skill : Skill.values()) {
            SkillEstimate est = estimates.getOrDefault(skill, SkillEstimate.none(skill));
            boolean reached = est.hasData() && est.rank().isAtLeast(target);
            if (reached) {
                reachedCount++;
            }

            List<Evidence> supporting = evidence.stream()
                    .filter(e -> e.skill() == skill)
                    .filter(e -> !e.date().isBefore(oldest) && !e.date().isAfter(today))
                    .filter(tiers::isPositive)
                    .filter(e -> e.level().isAtLeast(target))
                    .toList();
            boolean hasRecent = supporting.stream().anyMatch(e -> !e.date().isBefore(recent));

            boolean confidenceOk = rule.minConfidence() == MinConfidence.ESTIMATED
                    || est.confidence() == Confidence.RELIABLE;
            boolean ok = reached && supporting.size() >= rule.minPositive() && hasRecent && confidenceOk;
            allOk &= ok;

            statuses.add(new SkillStatus(skill, reached, supporting.size(), hasRecent, est.confidence(), ok));
        }

        boolean coreOk = !rule.requireCoreGrammar() || coreGrammarDone;
        return new GoalCompletion(allOk && coreOk, reachedCount >= config.nearGoalSkills(),
                statuses, rule.requireCoreGrammar(), coreGrammarDone);
    }
}
