package com.ichsprechedeutsch.coach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ichsprechedeutsch.coach.CoachInput.ArticleWindow;
import com.ichsprechedeutsch.coach.CoachInput.DoneActivity;
import com.ichsprechedeutsch.coach.CoachInput.ErrorTag;
import com.ichsprechedeutsch.coach.CoachInput.GrammarWindow;
import com.ichsprechedeutsch.coach.Recommendation.Category;
import com.ichsprechedeutsch.coach.WeeklyPlanner.DailyPlan;
import com.ichsprechedeutsch.coach.WeeklyPlanner.DayItem;
import com.ichsprechedeutsch.coach.WeeklyPlanner.WeekItem;
import com.ichsprechedeutsch.coach.WeeklyPlanner.WeeklyPlan;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.Skill;
import com.ichsprechedeutsch.common.model.StartMode;
import com.ichsprechedeutsch.config.CoachProperties;
import com.ichsprechedeutsch.config.TestConfig;
import com.ichsprechedeutsch.level.GoalCompletion;
import com.ichsprechedeutsch.level.GoalCompletion.SkillStatus;
import com.ichsprechedeutsch.level.Reassessment;
import com.ichsprechedeutsch.level.SkillEstimate;
import com.ichsprechedeutsch.level.SkillEstimate.Confidence;
import com.ichsprechedeutsch.level.SkillEstimate.Kind;
import com.ichsprechedeutsch.level.WorkingLevel;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CoachTest {

    private static final CoachProperties CONFIG = TestConfig.coach();
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);   // carsamba
    private final RuleBasedCoach coach = new RuleBasedCoach(CONFIG, TestConfig.srs());
    private final WeeklyPlanner planner = new WeeklyPlanner(CONFIG);

    /** Degistirilebilir girdi kurucu: varsayilan "aktif A2 kullanicisi, her beceride veri var". */
    static final class In {
        StartMode startMode = StartMode.SOME_KNOWLEDGE;
        Level target = Level.B1;
        int daily = 60;
        int days = 5;
        Level working = Level.A2;
        Map<Skill, SkillEstimate> skills = allSkills(Level.A2);
        GoalCompletion goal = null;
        Reassessment reassessment = new Reassessment(false, null, 0, 0);
        boolean placementDone = true;
        List<DoneActivity> activities = recentAllTypes();
        int dueReviews = 0;
        int dueChecks = 0;
        int dueMinutes = 0;
        List<GrammarWindow> grammar = List.of();
        List<ErrorTag> errors = List.of();
        Map<String, ArticleWindow> articles = Map.of();
        Set<StudyType> modules = EnumSet.noneOf(StudyType.class);

        CoachInput build() {
            return new CoachInput(TODAY, startMode, target, daily, days,
                    new WorkingLevel(working, WorkingLevel.Source.PLACEMENT, false), skills, goal, reassessment,
                    placementDone, activities, dueReviews, dueChecks, dueMinutes, 20, grammar, errors, articles,
                    modules);
        }
    }

    static Map<Skill, SkillEstimate> allSkills(Level level) {
        Map<Skill, SkillEstimate> m = new EnumMap<>(Skill.class);
        for (Skill s : Skill.values()) {
            m.put(s, new SkillEstimate(s, Kind.AT, level, Confidence.LOW, 1, List.of()));
        }
        return m;
    }

    /** Son iki gunde her ture calisma: hicbir "bosta" kurali tetiklenmez. */
    static List<DoneActivity> recentAllTypes() {
        List<DoneActivity> out = new ArrayList<>();
        for (StudyType t : StudyType.values()) {
            out.add(new DoneActivity(TODAY.minusDays(1), t, 20));
        }
        return out;
    }

    private List<Recommendation> advise(UnaryOperator<In> setup) {
        return coach.advise(setup.apply(new In()).build());
    }

    private static List<String> rules(List<Recommendation> recs) {
        return recs.stream().map(Recommendation::rule).toList();
    }

    @Nested
    class Rules {

        @Test
        void activeUserWithDataGetsNoRecommendation() {
            assertTrue(advise(i -> i).isEmpty());
        }

        @Test
        void someKnowledgeWithoutPlacementIsAskedToTakeTheTest() {
            List<Recommendation> r = advise(i -> {
                i.placementDone = false;
                return i;
            });
            assertEquals("ASSESSMENT", r.getFirst().rule());
            assertEquals("SITE", r.getFirst().action().kind());
            assertEquals(20, r.getFirst().minutes());
        }

        @Test
        void reassessmentDueIsRecommended() {
            List<Recommendation> r = advise(i -> {
                i.reassessment = new Reassessment(true, "DAYS", 50, 0);
                return i;
            });
            assertEquals(List.of("ASSESSMENT"), rules(r));
            assertTrue(r.getFirst().reason().contains("50 gün"));
        }

        @Test
        void comebackAfterThreeIdleDaysIsShortAndHistoryBased() {
            List<Recommendation> r = advise(i -> {
                i.activities = List.of(
                        new DoneActivity(TODAY.minusDays(5), StudyType.HOEREN, 40),
                        new DoneActivity(TODAY.minusDays(6), StudyType.LESEN, 10));
                i.dueReviews = 90;
                i.dueMinutes = 30;
                return i;
            });
            Recommendation first = r.getFirst();
            assertEquals("COMEBACK", first.rule());
            assertEquals(StudyType.HOEREN, first.type(), "en cok calisilan tur");
            assertEquals(30, first.minutes(), "gunluk surenin yarisi");
            assertTrue(first.reason().contains("5 gün"));
            // Birikmis 90 tekrar 3 gune yayilir: bugun 30.
            Recommendation reviews = r.stream().filter(x -> "DUE_REVIEWS".equals(x.rule())).findFirst().orElseThrow();
            assertTrue(reviews.title().contains("30 kelime"), reviews.title());
        }

        @Test
        void comebackNotTriggeredBeforeThreeDays() {
            List<Recommendation> r = advise(i -> {
                i.activities = List.of(new DoneActivity(TODAY.minusDays(2), StudyType.HOEREN, 40));
                return i;
            });
            assertFalse(rules(r).contains("COMEBACK"));
        }

        @Test
        void dueReviewsRespectDailyLimit() {
            Recommendation r = advise(i -> {
                i.dueReviews = 120;
                i.dueMinutes = 50;
                return i;
            }).getFirst();
            assertEquals("DUE_REVIEWS", r.rule());
            assertTrue(r.title().contains("50 kelime"));
            assertTrue(r.reason().contains("günlük sınır 50"));
            assertEquals(50, r.minutes());
        }

        @Test
        void recurringErrorNeedsThreeDistinctSessions() {
            assertTrue(advise(i -> {
                i.errors = List.of(new ErrorTag("DATIV", "Dativ", 2, "ACTIVE"));
                return i;
            }).isEmpty());
            Recommendation r = advise(i -> {
                i.errors = List.of(new ErrorTag("DATIV", "Dativ", 3, "ACTIVE"));
                return i;
            }).getFirst();
            assertEquals("RECURRING_ERROR", r.rule());
            assertTrue(r.reason().contains("3 farklı oturumda"));
        }

        @Test
        void resolvedErrorsAreIgnored() {
            assertTrue(advise(i -> {
                i.errors = List.of(new ErrorTag("DATIV", "Dativ", 5, "RESOLVED"));
                return i;
            }).isEmpty());
        }

        @Test
        void weakGrammarNeedsEnoughAnswersInWindow() {
            assertTrue(advise(i -> {
                i.grammar = List.of(new GrammarWindow("DATIV", "Dativ", 9, 2));
                return i;
            }).isEmpty(), "pencerede 10'dan az yanit");
            Recommendation r = advise(i -> {
                i.grammar = List.of(new GrammarWindow("DATIV", "Dativ", 20, 11),
                        new GrammarWindow("PERFEKT", "Perfekt", 20, 13));
                return i;
            }).getFirst();
            assertEquals("WEAK_GRAMMAR", r.rule());
            assertEquals("Konu tekrarı: Dativ", r.title(), "en zayif konu");
            assertTrue(r.reason().contains("Son 20 Dativ yanıtında %55"), r.reason());
        }

        @Test
        void grammarAboveThresholdIsFine() {
            assertTrue(advise(i -> {
                i.grammar = List.of(new GrammarWindow("DATIV", "Dativ", 20, 14));
                return i;
            }).isEmpty());
        }

        @Test
        void missingEvidenceSuggestsExternalWorkWithResult() {
            Recommendation r = advise(i -> {
                i.skills = new EnumMap<>(allSkills(Level.A2));
                i.skills.put(Skill.SPRECHEN, SkillEstimate.none(Skill.SPRECHEN));
                return i;
            }).getFirst();
            assertEquals("MISSING_EVIDENCE", r.rule());
            assertEquals(StudyType.SPRECHEN, r.type());
            assertEquals("EXTERNAL", r.action().kind(), "sitede Sprechen modulu yok");
            assertTrue(r.action().logResult());
        }

        @Test
        void idleSkillAfterSevenDays() {
            Recommendation r = advise(i -> {
                i.activities = new ArrayList<>(recentAllTypes());
                i.activities.removeIf(a -> a.type() == StudyType.SCHREIBEN);
                i.activities.add(new DoneActivity(TODAY.minusDays(8), StudyType.SCHREIBEN, 20));
                return i;
            }).getFirst();
            assertEquals("IDLE_SKILL", r.rule());
            assertEquals("Son 7 günde Schreiben çalışmadın.", r.reason());
        }

        @Test
        void rulesFiveAndSixAreOffAtA0() {
            List<Recommendation> r = advise(i -> {
                i.working = Level.A0;
                i.skills = new EnumMap<>(Skill.class);
                i.activities = List.of(new DoneActivity(TODAY.minusDays(1), StudyType.KELIME, 20));
                return i;
            });
            assertFalse(rules(r).contains("MISSING_EVIDENCE"));
            assertFalse(rules(r).contains("IDLE_SKILL"));
        }

        @Test
        void weakArticlePerType() {
            Recommendation r = advise(i -> {
                i.articles = Map.of("der", new ArticleWindow(20, 16), "die", new ArticleWindow(20, 11));
                return i;
            }).getFirst();
            assertEquals("WEAK_ARTICLE", r.rule());
            assertEquals("Artikel pratiği: die", r.title());
        }

        @Test
        void nearGoalRecommendsExamPractice() {
            List<SkillStatus> st = new ArrayList<>();
            for (Skill s : Skill.values()) {
                st.add(new SkillStatus(s, s != Skill.SPRECHEN, 1, true, Confidence.LOW, false));
            }
            Recommendation r = advise(i -> {
                i.goal = new GoalCompletion(false, true, st, false, false);
                return i;
            }).getFirst();
            assertEquals("NEAR_GOAL", r.rule());
            assertTrue(r.reason().startsWith("3 becerinde"));
            assertEquals(30, r.minutes());
        }
    }

    @Nested
    class Limits {

        @Test
        void atMostThreeAndOnePerCategory() {
            List<Recommendation> r = advise(i -> {
                i.placementDone = false;
                i.errors = List.of(new ErrorTag("DATIV", "Dativ", 4, "ACTIVE"));
                i.grammar = List.of(new GrammarWindow("PERFEKT", "Perfekt", 20, 5));
                i.skills = new EnumMap<>(Skill.class);
                i.articles = Map.of("das", new ArticleWindow(20, 5));
                return i;
            });
            assertEquals(3, r.size());
            assertEquals(List.of("ASSESSMENT", "RECURRING_ERROR", "MISSING_EVIDENCE"), rules(r),
                    "zayif gramer ikinci GRAMMAR onerisi olurdu; atlanir");
            assertEquals(3, r.stream().map(Recommendation::category).distinct().count());
        }

        @Test
        void everyRecommendationExplainsItself() {
            List<Recommendation> r = advise(i -> {
                i.dueReviews = 10;
                i.dueMinutes = 10;
                i.grammar = List.of(new GrammarWindow("DATIV", "Dativ", 20, 5));
                return i;
            });
            for (Recommendation x : r) {
                assertFalse(x.reason().isBlank());
                assertFalse(x.basis().isEmpty(), x.rule() + " hangi veriye dayandigini gostermeli");
                assertNotNull(x.action());
            }
        }

        @Test
        void siteModuleTurnsExternalIntoSiteLink() {
            Recommendation r = advise(i -> {
                i.grammar = List.of(new GrammarWindow("DATIV", "Dativ", 20, 5));
                i.modules = EnumSet.of(StudyType.GRAMER);
                return i;
            }).getFirst();
            assertEquals("SITE", r.action().kind());
        }
    }

    @Nested
    class NewUser {

        @Test
        void noDataGivesStartPlanFromWorkingLevel() {
            List<Recommendation> r = advise(i -> {
                i.startMode = StartMode.FROM_ZERO;
                i.working = Level.A0;
                i.skills = new EnumMap<>(Skill.class);
                i.activities = List.of();
                return i;
            });
            assertEquals(3, r.size());
            assertTrue(r.stream().allMatch(x -> "START".equals(x.rule())));
            // A0-A1 paylari: kelime 30, gramer 30, Hoeren 15 -> en yuksek uc tur, kategori basina bir.
            assertEquals(List.of(StudyType.KELIME, StudyType.GRAMER, StudyType.HOEREN),
                    r.stream().map(Recommendation::type).toList());
            assertTrue(r.getFirst().action().hint().contains("Alfabe"), "A0 yolu");
        }
    }

    @Nested
    class Plan {

        @Test
        void weeklyTotalFollowsRhythmAndShares() {
            CoachInput in = new In().build();
            WeeklyPlan w = planner.weekly(in, List.of());
            assertEquals(DayOfWeek.MONDAY, w.weekStart().getDayOfWeek());
            assertEquals(300, w.totalMinutes());
            WeekItem gramer = w.items().stream().filter(x -> x.type() == StudyType.GRAMER).findFirst().orElseThrow();
            assertEquals(25, gramer.sharePercent());
            assertEquals(75, gramer.targetMinutes());
            assertEquals(20, gramer.doneMinutes(), "dun calisilan gramer bu haftaya sayilir");
        }

        @Test
        void recommendationBoostsItsShare() {
            In setup = new In();
            setup.grammar = List.of(new GrammarWindow("DATIV", "Dativ", 20, 5));
            CoachInput in = setup.build();
            WeeklyPlan w = planner.weekly(in, coach.advise(in));
            WeekItem gramer = w.items().stream().filter(x -> x.type() == StudyType.GRAMER).findFirst().orElseThrow();
            assertTrue(gramer.sharePercent() > 25, "oneri gramer payini artirir");
            assertEquals(List.of(StudyType.GRAMER), w.boosted());
            assertEquals(100, w.items().stream().mapToInt(WeekItem::sharePercent).sum(), 1);
        }

        @Test
        void dailyPlanFitsDailyMinutesAndStartsWithRecommendations() {
            In setup = new In();
            setup.grammar = List.of(new GrammarWindow("DATIV", "Dativ", 20, 5));
            CoachInput in = setup.build();
            List<Recommendation> recs = coach.advise(in);
            DailyPlan d = planner.daily(in, recs, planner.weekly(in, recs));
            assertEquals(60, d.totalMinutes());
            assertEquals(60, d.items().stream().mapToInt(DayItem::minutes).sum());
            assertEquals("RECOMMENDATION", d.items().getFirst().source());
            assertEquals(StudyType.GRAMER, d.items().getFirst().type());
            for (DayItem it : d.items()) {
                assertTrue(it.minutes() >= CONFIG.planMinItemMinutes());
                assertEquals(0, it.minutes() % CONFIG.planBlockMinutes());
            }
        }

        @Test
        void comebackDayIsShorter() {
            In setup = new In();
            setup.activities = List.of(new DoneActivity(TODAY.minusDays(5), StudyType.HOEREN, 40));
            CoachInput in = setup.build();
            List<Recommendation> recs = coach.advise(in);
            DailyPlan d = planner.daily(in, recs, planner.weekly(in, recs));
            assertEquals(30, d.totalMinutes());
            assertEquals(30, d.items().stream().mapToInt(DayItem::minutes).sum());
        }

        @Test
        void fifteenMinuteDayStillMakesSense() {
            In setup = new In();
            setup.daily = 15;
            CoachInput in = setup.build();
            DailyPlan d = planner.daily(in, List.of(), planner.weekly(in, List.of()));
            assertEquals(15, d.items().stream().mapToInt(DayItem::minutes).sum());
        }
    }
}
