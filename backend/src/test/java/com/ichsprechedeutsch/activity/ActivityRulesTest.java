package com.ichsprechedeutsch.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ichsprechedeutsch.activity.ActivityRules.Candidate;
import com.ichsprechedeutsch.activity.ActivityRules.EvidenceDraft;
import com.ichsprechedeutsch.activity.ActivityRules.Origin;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.ResultSource;
import com.ichsprechedeutsch.common.model.Skill;
import com.ichsprechedeutsch.config.TestConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ActivityRulesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 22);
    private static final int GAP = TestConfig.activity().idleGapMinutes();

    private static Candidate external(ActivityType type, Level level, Double score, Double max, ResultSource src) {
        return new Candidate(type, Origin.EXTERNAL, level, score, max, src, DAY, null);
    }

    @Test
    void skillWithLevelAndScoreBecomesEvidence() {
        Optional<EvidenceDraft> e = ActivityRules.evidenceFrom(
                external(ActivityType.SCHREIBEN, Level.A2, 17.0, 20.0, ResultSource.MODELLTEST));
        assertTrue(e.isPresent());
        assertEquals(Skill.SCHREIBEN, e.get().skill());
        assertEquals(ResultSource.MODELLTEST, e.get().source());
        assertNull(e.get().contentVerified());
    }

    @Test
    void durationOnlyIsJustHistory() {
        assertTrue(ActivityRules.evidenceFrom(
                external(ActivityType.HOEREN, Level.A2, null, null, null)).isEmpty());
    }

    @Test
    void grammarAndVocabularyNeverBecomeEvidence() {
        assertTrue(ActivityRules.evidenceFrom(
                external(ActivityType.GRAMER, Level.A2, 9.0, 10.0, ResultSource.APP_TEST)).isEmpty());
        assertTrue(ActivityRules.evidenceFrom(
                external(ActivityType.KELIME, Level.A2, 9.0, 10.0, ResultSource.APP_TEST)).isEmpty());
    }

    @Test
    void levelAndResultSourceAreRequired() {
        assertTrue(ActivityRules.evidenceFrom(
                external(ActivityType.LESEN, null, 8.0, 10.0, ResultSource.TEACHER)).isEmpty());
        assertTrue(ActivityRules.evidenceFrom(
                external(ActivityType.LESEN, Level.A2, 8.0, 10.0, null)).isEmpty());
    }

    @Test
    void siteResultsAreSiteTestsAndCarryVerifiedFlag() {
        Candidate site = new Candidate(ActivityType.LESEN, Origin.SITE, Level.A2, 5.0, 6.0, null, DAY, false);
        EvidenceDraft e = ActivityRules.evidenceFrom(site).orElseThrow();
        assertEquals(ResultSource.SITE_TEST, e.source());
        assertEquals(Boolean.FALSE, e.contentVerified());
    }

    @Test
    void siteHoerenIsLearningActivityNotEvidence() {
        Candidate site = new Candidate(ActivityType.HOEREN, Origin.SITE, Level.A2, 5.0, 6.0, null, DAY, false);
        assertTrue(ActivityRules.evidenceFrom(site).isEmpty());
    }

    @Test
    void activeTimeSkipsLongGaps() {
        Instant t = Instant.parse("2026-09-22T10:00:00Z");
        List<Instant> events = List.of(t, t.plusSeconds(60), t.plusSeconds(120),
                t.plusSeconds(120 + 20 * 60), t.plusSeconds(120 + 20 * 60 + 30));
        // 60 + 60 sayilir, 20 dk'lik bosluk sayilmaz, son 30 sn sayilir.
        assertEquals(150, ActivityRules.activeSeconds(events, GAP));
    }

    @Test
    void activeTimeCountsGapExactlyAtLimit() {
        Instant t = Instant.parse("2026-09-22T10:00:00Z");
        assertEquals(GAP * 60L, ActivityRules.activeSeconds(List.of(t, t.plusSeconds(GAP * 60L)), GAP));
        assertEquals(0, ActivityRules.activeSeconds(List.of(t), GAP));
    }
}
