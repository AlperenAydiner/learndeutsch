package com.ichsprechedeutsch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.ResultSource;
import com.ichsprechedeutsch.common.model.Tier;
import java.time.DayOfWeek;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Ek A degerleri application.yml'den dogru baglaniyor mu (K7). */
class ConfigBindingTest {

    @Test
    void levelDefaultsMatchAppendixA() {
        LevelProperties c = TestConfig.level();
        assertEquals(0.60, c.passThreshold());
        assertEquals(1.0, c.weight(Tier.HIGH));
        assertEquals(0.6, c.weight(Tier.MEDIUM));
        assertEquals(0.3, c.weight(Tier.LOW));
        assertTrue(c.unverifiedDowngrade());
        assertEquals(12, c.maxEvidenceAgeMonths());
        assertEquals(2, c.reliable().minPositive());
        assertEquals(30, c.reliable().recentDays());
        assertEquals(LevelProperties.OverallMethod.MEDIAN, c.overall().method());
        assertEquals(2, c.overall().minSkills());
        assertEquals(0.5, c.canDoScores().get(LevelProperties.CanDoAnswer.PARTIAL));
        assertEquals(4, c.schreibenRubric().criteria());
        assertEquals(3, c.schreibenRubric().maxPerCriterion());
        assertEquals(Level.A1, c.defaultWorkingLevelSomeKnowledge());
        assertEquals(45, c.reassessment().days());
        assertEquals(6, c.reassessment().newEvidence());
        assertEquals(LevelProperties.MinConfidence.ESTIMATED, c.goalCompletion().minConfidence());
        assertFalse(c.goalCompletion().requireCoreGrammar());
        assertEquals(3, c.nearGoalSkills());
        assertEquals(Tier.HIGH, c.sourceTiers().get(ResultSource.OFFICIAL_EXAM));
        assertEquals(Tier.LOW, c.sourceTiers().get(ResultSource.SELF_ASSESSMENT));
        assertEquals(Tier.LOW, c.selfScoredProductiveModelltestTier());
        for (ResultSource s : ResultSource.values()) {
            assertTrue(c.sourceTiers().containsKey(s), "kademe eslesmesi eksik: " + s);
        }
    }

    @Test
    void placementUsesDecisionK003() {
        PlacementProperties p = TestConfig.placement();
        assertEquals(Level.A2, p.startLevel());
        assertEquals(6, p.blockSize());
        assertEquals(24, p.maxQuestions());
        assertEquals(0.60, p.passThreshold());
        assertEquals(0.80, p.upThreshold());
    }

    @Test
    void otherSectionsBind() {
        assertEquals(5, TestConfig.activity().idleGapMinutes());
        CoachProperties coach = TestConfig.coach();
        assertEquals(3, coach.maxRecommendations());
        assertEquals(1, coach.maxPerCategory());
        assertEquals(DayOfWeek.MONDAY, coach.weekStart());
        assertEquals(20, coach.grammar().size());
        assertEquals(List.of(7, 30), coach.grammarCheckDays());
        assertEquals(30, coach.weeklyShares().get("basic").get("kelime"));
        assertEquals("advanced", coach.sharesByLevel().get(Level.C1));
        coach.weeklyShares().forEach((group, shares) ->
                assertEquals(100, shares.values().stream().mapToInt(Integer::intValue).sum(),
                        group + " paylari toplami 100 olmali"));
        SrsProperties srs = TestConfig.srs();
        assertEquals(List.of(1, 3, 7, 14, 30, 60, 120), srs.intervalsDays());
        assertEquals(8, srs.dailyNewByMinutes().get(30));
        assertEquals(50, srs.dailyReviewLimit());
        assertEquals(AnswerProperties.Mode.ACCEPT_WARN, TestConfig.answer().letterCase());
    }
}
