package com.ichsprechedeutsch.level;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.Skill;
import com.ichsprechedeutsch.common.model.StartMode;
import com.ichsprechedeutsch.config.LevelProperties;
import com.ichsprechedeutsch.level.LevelStore.Assessment;
import com.ichsprechedeutsch.onboarding.OnboardingStore;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seviye modelini veriyle besler: kanitlari ve degerlendirmeleri okur, saf
 * siniflara (SkillEstimator, OverallEstimate, WorkingLevel, Reassessment,
 * GoalCompletion) bugunun tarihiyle birlikte verir.
 */
@Service
public class LevelService {

    /** Koclukta gosterilecek tum seviye bilgisi. */
    public record Overview(
            Map<Skill, SkillEstimate> skills,
            OverallEstimate overall,
            WorkingLevel working,
            Assessment placement,
            Reassessment reassessment,
            GoalCompletion goal,
            Level goalTarget) {
    }

    private final LevelStore store;
    private final OnboardingStore onboarding;
    private final LevelProperties config;
    private final SkillEstimator estimator;

    public LevelService(LevelStore store, OnboardingStore onboarding, LevelProperties config) {
        this.store = store;
        this.onboarding = onboarding;
        this.config = config;
        this.estimator = new SkillEstimator(config);
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID userId, LocalDate today) {
        List<Evidence> evidence = store.evidence(userId);

        Map<Skill, SkillEstimate> skills = new EnumMap<>(Skill.class);
        for (Skill s : Skill.values()) {
            skills.put(s, estimator.estimate(s, evidence, today));
        }
        OverallEstimate overall = OverallEstimate.from(skills, config);

        Assessment placement = store.latestAssessment(userId, "PLACEMENT").orElse(null);
        StartMode startMode = onboarding.profile(userId).map(OnboardingStore.Profile::startMode).orElse(null);
        // A0 temel konulari gramer modulu gelince (Faz 3b) hesaplanir.
        WorkingLevel working = WorkingLevel.resolve(overall,
                placement == null ? null : placement.result(), startMode, false, config);

        Assessment last = store.latestAssessment(userId, null).orElse(null);
        Reassessment reassessment = last == null
                ? Reassessment.check(null, 0, today, config)
                : Reassessment.check(last.takenOn(), store.evidenceCountSince(userId, last.takenOn()),
                        today, config);

        Level target = onboarding.activeGoal(userId).map(OnboardingStore.Goal::target).orElse(null);
        GoalCompletion goal = target == null ? null
                : GoalCompletion.evaluate(target, skills, evidence, false, today, config);

        return new Overview(skills, overall, working, placement, reassessment, goal, target);
    }
}
