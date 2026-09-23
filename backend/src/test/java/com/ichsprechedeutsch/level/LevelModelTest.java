package com.ichsprechedeutsch.level;

import static com.ichsprechedeutsch.common.model.Level.A0;
import static com.ichsprechedeutsch.common.model.Level.A1;
import static com.ichsprechedeutsch.common.model.Level.A2;
import static com.ichsprechedeutsch.common.model.Level.B1;
import static com.ichsprechedeutsch.common.model.Level.B2;
import static com.ichsprechedeutsch.common.model.ResultSource.APP_TEST;
import static com.ichsprechedeutsch.common.model.ResultSource.MODELLTEST;
import static com.ichsprechedeutsch.common.model.ResultSource.OFFICIAL_EXAM;
import static com.ichsprechedeutsch.common.model.ResultSource.SELF_ASSESSMENT;
import static com.ichsprechedeutsch.common.model.ResultSource.SITE_TEST;
import static com.ichsprechedeutsch.common.model.ResultSource.TEACHER;
import static com.ichsprechedeutsch.common.model.Skill.HOEREN;
import static com.ichsprechedeutsch.common.model.Skill.LESEN;
import static com.ichsprechedeutsch.common.model.Skill.SCHREIBEN;
import static com.ichsprechedeutsch.common.model.Skill.SPRECHEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.ResultSource;
import com.ichsprechedeutsch.common.model.Skill;
import com.ichsprechedeutsch.common.model.StartMode;
import com.ichsprechedeutsch.common.model.Tier;
import com.ichsprechedeutsch.config.LevelProperties;
import com.ichsprechedeutsch.config.TestConfig;
import com.ichsprechedeutsch.level.SkillEstimate.Confidence;
import com.ichsprechedeutsch.level.SkillEstimate.Kind;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LevelModelTest {

    private static final LevelProperties CONFIG = TestConfig.level();
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);
    private final SkillEstimator estimator = new SkillEstimator(CONFIG);

    private static Evidence ev(Skill skill, Level level, ResultSource source, double score, double max,
                               int daysAgo) {
        return new Evidence(UUID.randomUUID(), skill, level, source, score, max, TODAY.minusDays(daysAgo), null);
    }

    private static Evidence pass(Skill skill, Level level, ResultSource source, int daysAgo) {
        return ev(skill, level, source, 8, 10, daysAgo);
    }

    private static Evidence fail(Skill skill, Level level, ResultSource source, int daysAgo) {
        return ev(skill, level, source, 3, 10, daysAgo);
    }

    @Nested
    class Tiers {
        private final TierPolicy tiers = new TierPolicy(CONFIG);

        @Test
        void sourceMapsToTier() {
            assertEquals(Tier.HIGH, tiers.tierOf(pass(LESEN, A2, OFFICIAL_EXAM, 1)));
            assertEquals(Tier.MEDIUM, tiers.tierOf(pass(LESEN, A2, TEACHER, 1)));
            assertEquals(Tier.MEDIUM, tiers.tierOf(pass(LESEN, A2, MODELLTEST, 1)));
            assertEquals(Tier.LOW, tiers.tierOf(pass(LESEN, A2, APP_TEST, 1)));
            assertEquals(Tier.LOW, tiers.tierOf(pass(SPRECHEN, A2, SELF_ASSESSMENT, 1)));
        }

        @Test
        void selfScoredProductiveModelltestIsLow() {
            assertEquals(Tier.LOW, tiers.tierOf(pass(SCHREIBEN, A2, MODELLTEST, 1)));
            assertEquals(Tier.LOW, tiers.tierOf(pass(SPRECHEN, A2, MODELLTEST, 1)));
        }

        @Test
        void unverifiedContentIsOneTierLower() {
            Evidence site = new Evidence(UUID.randomUUID(), LESEN, A2, SITE_TEST, 8, 10, TODAY, false);
            Evidence verified = new Evidence(UUID.randomUUID(), LESEN, A2, SITE_TEST, 8, 10, TODAY, true);
            assertEquals(Tier.LOW, tiers.tierOf(site));
            assertEquals(Tier.MEDIUM, tiers.tierOf(verified));
        }

        @Test
        void passThresholdIsInclusive() {
            assertTrue(tiers.isPositive(ev(LESEN, A2, TEACHER, 6, 10, 1)));
            assertFalse(tiers.isPositive(ev(LESEN, A2, TEACHER, 5.9, 10, 1)));
        }

        @Test
        void evidenceRejectsImpossibleScores() {
            assertThrows(IllegalArgumentException.class, () -> ev(LESEN, A2, TEACHER, 11, 10, 1));
            assertThrows(IllegalArgumentException.class, () -> ev(LESEN, A0, TEACHER, 5, 10, 1));
        }
    }

    @Nested
    class SkillEstimates {

        @Test
        void noEvidenceMeansNoData() {
            SkillEstimate e = estimator.estimate(LESEN, List.of(), TODAY);
            assertEquals(Kind.NONE, e.kind());
            assertEquals(Confidence.NO_DATA, e.confidence());
            assertNull(e.rank());
        }

        @Test
        void otherSkillsEvidenceIsIgnored() {
            SkillEstimate e = estimator.estimate(HOEREN, List.of(pass(LESEN, B1, TEACHER, 1)), TODAY);
            assertEquals(Kind.NONE, e.kind());
        }

        @Test
        void singlePassWithNothingAboveIsALowerBound() {
            SkillEstimate e = estimator.estimate(LESEN, List.of(pass(LESEN, A2, APP_TEST, 3)), TODAY);
            assertEquals(Kind.AT_LEAST, e.kind());
            assertEquals(A2, e.level());
            assertEquals(Confidence.LOW, e.confidence());
            assertEquals(1, e.basis().size());
        }

        @Test
        void failAboveTurnsLowerBoundIntoEstimate() {
            SkillEstimate e = estimator.estimate(LESEN,
                    List.of(pass(LESEN, A2, TEACHER, 3), fail(LESEN, B1, TEACHER, 2)), TODAY);
            assertEquals(Kind.AT, e.kind());
            assertEquals(A2, e.level());
        }

        @Test
        void onlyFailuresGiveBelowTheLowestFailedLevel() {
            SkillEstimate e = estimator.estimate(LESEN,
                    List.of(fail(LESEN, B2, TEACHER, 3), fail(LESEN, B1, TEACHER, 2)), TODAY);
            assertEquals(Kind.BELOW, e.kind());
            assertEquals(B1, e.level());
            assertEquals(A2, e.rank());
            assertEquals(Confidence.LOW, e.confidence());
        }

        @Test
        void failingA1RanksAsA0() {
            SkillEstimate e = estimator.estimate(LESEN, List.of(fail(LESEN, A1, TEACHER, 1)), TODAY);
            assertEquals(Kind.BELOW, e.kind());
            assertEquals(A0, e.rank());
        }

        @Test
        void heavierNegativeEvidenceWinsAtItsLevel() {
            // B1: destek 0.3 (dusuk) < karsi 0.6 (orta) -> B1 desteklenmez; A2'ye karsi kanit yok.
            SkillEstimate e = estimator.estimate(LESEN,
                    List.of(pass(LESEN, B1, APP_TEST, 3), fail(LESEN, B1, TEACHER, 2)), TODAY);
            assertEquals(A2, e.level());
            assertEquals(Kind.AT, e.kind());
        }

        @Test
        void evidenceOlderThanWindowIsIgnored() {
            SkillEstimate e = estimator.estimate(LESEN,
                    List.of(pass(LESEN, B2, OFFICIAL_EXAM, 400)), TODAY);
            assertEquals(Kind.NONE, e.kind());
        }

        @Test
        void futureEvidenceIsIgnored() {
            SkillEstimate e = estimator.estimate(LESEN, List.of(pass(LESEN, B1, TEACHER, -1)), TODAY);
            assertEquals(Kind.NONE, e.kind());
        }

        @Test
        void reliableNeedsCountRecencyAndStrength() {
            List<Evidence> strong = List.of(pass(LESEN, A2, TEACHER, 5), pass(LESEN, A2, APP_TEST, 60));
            assertEquals(Confidence.RELIABLE, estimator.estimate(LESEN, strong, TODAY).confidence());

            List<Evidence> onlyLow = List.of(pass(LESEN, A2, APP_TEST, 5), pass(LESEN, A2, SELF_ASSESSMENT, 6));
            assertEquals(Confidence.LOW, estimator.estimate(LESEN, onlyLow, TODAY).confidence());

            List<Evidence> old = List.of(pass(LESEN, A2, TEACHER, 40), pass(LESEN, A2, TEACHER, 50));
            assertEquals(Confidence.LOW, estimator.estimate(LESEN, old, TODAY).confidence());

            List<Evidence> single = List.of(pass(LESEN, A2, OFFICIAL_EXAM, 1));
            assertEquals(Confidence.LOW, estimator.estimate(LESEN, single, TODAY).confidence());
        }

        @Test
        void higherPassSupportsLowerLevels() {
            SkillEstimate e = estimator.estimate(LESEN,
                    List.of(pass(LESEN, B1, TEACHER, 3), fail(LESEN, B2, TEACHER, 2)), TODAY);
            assertEquals(B1, e.level());
            assertEquals(Kind.AT, e.kind());
        }
    }

    @Nested
    class Overall {

        private Map<Skill, SkillEstimate> skills(Level... perSkill) {
            Map<Skill, SkillEstimate> m = new EnumMap<>(Skill.class);
            Skill[] all = Skill.values();
            for (int i = 0; i < perSkill.length; i++) {
                Level l = perSkill[i];
                m.put(all[i], l == null ? SkillEstimate.none(all[i])
                        : new SkillEstimate(all[i], Kind.AT, l, Confidence.LOW, 1, List.of()));
            }
            return m;
        }

        @Test
        void tooFewSkillsIsInsufficient() {
            OverallEstimate o = OverallEstimate.from(skills(A2, null, null, null), CONFIG);
            assertFalse(o.sufficient());
            assertNull(o.level());
            assertEquals(List.of(HOEREN, SCHREIBEN, SPRECHEN), o.missing());
        }

        @Test
        void medianRoundsDownWithEvenCount() {
            OverallEstimate o = OverallEstimate.from(skills(A1, B1, null, null), CONFIG);
            assertTrue(o.sufficient());
            assertEquals(A1, o.level());
            assertEquals(A1, o.rangeMin());
            assertEquals(B1, o.rangeMax());
            assertEquals(LESEN, o.weakest());
        }

        @Test
        void medianOfThree() {
            OverallEstimate o = OverallEstimate.from(skills(B2, A2, B1, null), CONFIG);
            assertEquals(B1, o.level());
            assertEquals(HOEREN, o.weakest());
            assertEquals(List.of(SPRECHEN), o.missing());
        }
    }

    @Nested
    class Working {

        @Test
        void overallEstimateComesFirst() {
            OverallEstimate overall = new OverallEstimate(true, B1, A2, B2, LESEN, List.of(),
                    LevelProperties.OverallMethod.MEDIAN);
            WorkingLevel w = WorkingLevel.resolve(overall, A1, StartMode.SOME_KNOWLEDGE, false, CONFIG);
            assertEquals(B1, w.level());
            assertEquals(WorkingLevel.Source.OVERALL_ESTIMATE, w.source());
        }

        @Test
        void placementWhenNoOverall() {
            WorkingLevel w = WorkingLevel.resolve(null, A2, StartMode.SOME_KNOWLEDGE, false, CONFIG);
            assertEquals(A2, w.level());
            assertEquals(WorkingLevel.Source.PLACEMENT, w.source());
            assertFalse(w.recommendPlacement());
        }

        @Test
        void someKnowledgeWithoutTestUsesDefaultAndRecommendsTest() {
            WorkingLevel w = WorkingLevel.resolve(null, null, StartMode.SOME_KNOWLEDGE, false, CONFIG);
            assertEquals(A1, w.level());
            assertTrue(w.recommendPlacement());
        }

        @Test
        void fromZeroIsA0AndLeavesAfterCoreTopics() {
            assertEquals(A0, WorkingLevel.resolve(null, null, StartMode.FROM_ZERO, false, CONFIG).level());
            WorkingLevel out = WorkingLevel.resolve(null, null, StartMode.FROM_ZERO, true, CONFIG);
            assertEquals(A1, out.level());
            assertEquals(WorkingLevel.Source.A0_EXIT, out.source());
        }

        @Test
        void belowA1PlacementIsA0() {
            assertEquals(A0, WorkingLevel.resolve(null, A0, StartMode.SOME_KNOWLEDGE, false, CONFIG).level());
        }

        @Test
        void skillEstimateOverridesForThatSkill() {
            WorkingLevel w = WorkingLevel.resolve(null, A2, StartMode.SOME_KNOWLEDGE, false, CONFIG);
            SkillEstimate lesen = new SkillEstimate(LESEN, Kind.AT, B1, Confidence.LOW, 1, List.of());
            assertEquals(B1, w.forSkill(lesen));
            assertEquals(A2, w.forSkill(SkillEstimate.none(HOEREN)));
        }
    }

    @Nested
    class Reassess {

        @Test
        void neverAssessedIsNotDue() {
            assertFalse(Reassessment.check(null, 10, TODAY, CONFIG).due());
        }

        @Test
        void dueAfterDaysOrNewEvidence() {
            assertTrue(Reassessment.check(TODAY.minusDays(45), 0, TODAY, CONFIG).due());
            assertFalse(Reassessment.check(TODAY.minusDays(44), 5, TODAY, CONFIG).due());
            Reassessment byEvidence = Reassessment.check(TODAY.minusDays(3), 6, TODAY, CONFIG);
            assertTrue(byEvidence.due());
            assertEquals("NEW_EVIDENCE", byEvidence.reason());
        }
    }

    @Nested
    class Goal {

        private List<Evidence> twoRecentPasses(Level level) {
            return List.of(
                    pass(LESEN, level, TEACHER, 5), pass(LESEN, level, APP_TEST, 50),
                    pass(HOEREN, level, TEACHER, 5), pass(HOEREN, level, APP_TEST, 50),
                    pass(SCHREIBEN, level, TEACHER, 5), pass(SCHREIBEN, level, APP_TEST, 50),
                    pass(SPRECHEN, level, TEACHER, 5), pass(SPRECHEN, level, SELF_ASSESSMENT, 50));
        }

        private Map<Skill, SkillEstimate> estimates(List<Evidence> evidence) {
            Map<Skill, SkillEstimate> m = new EnumMap<>(Skill.class);
            for (Skill s : Skill.values()) {
                m.put(s, estimator.estimate(s, evidence, TODAY));
            }
            return m;
        }

        @Test
        void completeWhenEverySkillHasEnoughRecentSupport() {
            List<Evidence> evidence = twoRecentPasses(A2);
            GoalCompletion g = GoalCompletion.evaluate(A2, estimates(evidence), evidence, false, TODAY, CONFIG);
            assertTrue(g.complete());
            assertTrue(g.nearGoal());
            assertEquals(4, g.skills().size());
        }

        @Test
        void aboveTargetEvidenceAlsoCounts() {
            List<Evidence> evidence = twoRecentPasses(B1);
            assertTrue(GoalCompletion.evaluate(A2, estimates(evidence), evidence, false, TODAY, CONFIG).complete());
        }

        @Test
        void missingSkillBlocksCompletionButCanBeNearGoal() {
            List<Evidence> evidence = twoRecentPasses(A2).stream().filter(e -> e.skill() != SPRECHEN).toList();
            GoalCompletion g = GoalCompletion.evaluate(A2, estimates(evidence), evidence, false, TODAY, CONFIG);
            assertFalse(g.complete());
            assertTrue(g.nearGoal());
            GoalCompletion.SkillStatus sprechen = g.skills().stream()
                    .filter(s -> s.skill() == SPRECHEN).findFirst().orElseThrow();
            assertFalse(sprechen.ok());
            assertEquals(Confidence.NO_DATA, sprechen.confidence());
        }

        @Test
        void supportMustIncludeARecentEvidence() {
            List<Evidence> evidence = List.of(
                    pass(LESEN, A2, TEACHER, 40), pass(LESEN, A2, TEACHER, 50),
                    pass(HOEREN, A2, TEACHER, 40), pass(HOEREN, A2, TEACHER, 50),
                    pass(SCHREIBEN, A2, TEACHER, 40), pass(SCHREIBEN, A2, TEACHER, 50),
                    pass(SPRECHEN, A2, TEACHER, 40), pass(SPRECHEN, A2, TEACHER, 50));
            GoalCompletion g = GoalCompletion.evaluate(A2, estimates(evidence), evidence, false, TODAY, CONFIG);
            assertFalse(g.complete());
        }

        @Test
        void belowTargetIsNotReached() {
            List<Evidence> evidence = twoRecentPasses(A1);
            GoalCompletion g = GoalCompletion.evaluate(B2, estimates(evidence), evidence, false, TODAY, CONFIG);
            assertFalse(g.complete());
            assertFalse(g.nearGoal());
        }
    }
}
