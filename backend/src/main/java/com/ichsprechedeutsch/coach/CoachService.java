package com.ichsprechedeutsch.coach;

import com.ichsprechedeutsch.activity.ActivityStore;
import com.ichsprechedeutsch.article.ArticleService;
import com.ichsprechedeutsch.coach.CoachInput.ArticleWindow;
import com.ichsprechedeutsch.coach.CoachInput.DoneActivity;
import com.ichsprechedeutsch.coach.CoachInput.ErrorTag;
import com.ichsprechedeutsch.coach.CoachInput.GrammarWindow;
import com.ichsprechedeutsch.coach.WeeklyPlanner.DailyPlan;
import com.ichsprechedeutsch.coach.WeeklyPlanner.WeeklyPlan;
import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.config.CoachProperties;
import com.ichsprechedeutsch.config.PlacementProperties;
import com.ichsprechedeutsch.content.ContentCatalog;
import com.ichsprechedeutsch.content.Question;
import com.ichsprechedeutsch.grammar.GrammarService;
import com.ichsprechedeutsch.learning.LearningAnswerStore;
import com.ichsprechedeutsch.learning.LearningAnswerStore.Kind;
import com.ichsprechedeutsch.level.LevelService;
import com.ichsprechedeutsch.level.LevelService.Overview;
import com.ichsprechedeutsch.onboarding.OnboardingStore;
import com.ichsprechedeutsch.onboarding.OnboardingStore.Goal;
import com.ichsprechedeutsch.onboarding.OnboardingStore.Profile;
import com.ichsprechedeutsch.onboarding.OnboardingStore.Rhythm;
import com.ichsprechedeutsch.vocabulary.VocabularyService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Koc motorunu veriyle besler: girdiyi toplar, {@link Coach}'a verir,
 * haftalik ve gunluk plani hesaplar. Aliskanlik ozeti (son 7 gun, son 30
 * gun) de buradan gelir; bunlar ogrenme aktivitesidir, seviye degildir (K2).
 */
@Service
public class CoachService {

    /** Gecmis bu kadar gune kadar okunur: bosta kalma, geri donus, en cok calisilan tur. */
    private static final int HISTORY_DAYS = 60;
    private static final int TOTALS_DAYS = 30;
    private static final int HABIT_DAYS = 7;

    public record Day(LocalDate date, Map<StudyType, Integer> minutes) {
    }

    public record View(List<Recommendation> recommendations, WeeklyPlan weekly, DailyPlan daily,
                       List<Day> last7Days, Map<StudyType, Integer> last30Days) {
    }

    private final Coach coach;
    private final WeeklyPlanner planner;
    private final LevelService levels;
    private final OnboardingStore onboarding;
    private final ActivityStore activities;
    private final ContentCatalog catalog;
    private final PlacementProperties placement;
    private final VocabularyService vocabulary;
    private final ArticleService article;
    private final GrammarService grammar;
    private final LearningAnswerStore learningAnswers;
    private final CoachProperties config;

    public CoachService(Coach coach, CoachProperties config, LevelService levels, OnboardingStore onboarding,
                        ActivityStore activities, ContentCatalog catalog, PlacementProperties placement,
                        VocabularyService vocabulary, ArticleService article, GrammarService grammar,
                        LearningAnswerStore learningAnswers) {
        this.coach = coach;
        this.planner = new WeeklyPlanner(config);
        this.levels = levels;
        this.onboarding = onboarding;
        this.activities = activities;
        this.catalog = catalog;
        this.placement = placement;
        this.vocabulary = vocabulary;
        this.article = article;
        this.grammar = grammar;
        this.learningAnswers = learningAnswers;
        this.config = config;
    }

    @Transactional(readOnly = true)
    public View view(UUID userId, LocalDate today) {
        CoachInput in = input(userId, today);
        List<Recommendation> recs = coach.advise(in);
        WeeklyPlan weekly = planner.weekly(in, recs);
        DailyPlan daily = planner.daily(in, recs, weekly);
        return new View(recs, weekly, daily, habit(in), WeeklyPlanner.minutesByType(in.activities(),
                today.minusDays(TOTALS_DAYS - 1), today));
    }

    CoachInput input(UUID userId, LocalDate today) {
        Profile profile = onboarding.profile(userId).orElse(null);
        Goal goal = onboarding.activeGoal(userId).orElse(null);
        Rhythm rhythm = onboarding.rhythm(userId).orElse(null);
        if (profile == null || goal == null || rhythm == null) {
            throw new NotFoundException("Önce başlangıç adımlarını tamamla");
        }
        Overview o = levels.overview(userId, today);

        List<DoneActivity> done = new ArrayList<>();
        for (ActivityStore.Activity a : activities.between(userId, today.minusDays(HISTORY_DAYS), today)) {
            StudyType.of(a.type()).ifPresent(t -> done.add(new DoneActivity(a.date(), t, a.durationMinutes())));
        }

        // Kelime, artikel (Faz 3a) ve gramer + hata hafizasi (Faz 3b) gercek veriden.
        int dueReviews = vocabulary.stats(userId, today).dueToday();
        Map<String, ArticleWindow> articleWindows = new LinkedHashMap<>();
        article.windows(userId).forEach((a, w) -> articleWindows.put(a, new ArticleWindow(w.answers(), w.correct())));

        List<GrammarWindow> grammarWindows = new ArrayList<>();
        learningAnswers.windowsByTag(userId, Kind.GRAMMAR, config.grammar().size())
                .forEach((topicId, w) -> grammarWindows.add(new GrammarWindow(topicId,
                        grammar.etiketAdi(topicId), w.answers(), w.correct())));

        List<ErrorTag> recurring = grammar.unresolvedErrors(userId).stream()
                .map(e -> new ErrorTag(e.tag(), grammar.etiketAdi(e.tag()), e.distinctSessions(),
                        e.status().name()))
                .toList();

        return new CoachInput(today, profile.startMode(), goal.target(), rhythm.dailyMinutes(),
                rhythm.daysPerWeek(), o.working(), o.skills(), o.goal(), o.reassessment(), o.placement() != null,
                done, dueReviews, grammar.dueChecks(userId, today), vocabulary.dueMinutes(userId, today),
                placementMinutes(), grammarWindows, recurring, articleWindows,
                siteModules(o.working().level()));
    }

    /** Sitede icerigi olan calisma turleri: koc "sitede yap" diyebilsin diye. */
    private Set<StudyType> siteModules(Level level) {
        Level working = level.forContent();
        Set<StudyType> out = EnumSet.noneOf(StudyType.class);
        boolean kelimeVar = catalog.words().values().stream().anyMatch(w -> working.isAtLeast(w.level()));
        if (kelimeVar) {
            out.add(StudyType.KELIME);
        }
        boolean gramerVar = catalog.lessons().values().stream().anyMatch(l -> working.isAtLeast(l.level()));
        if (gramerVar) {
            out.add(StudyType.GRAMER);
        }
        if (catalog.readings().values().stream().anyMatch(r -> working.isAtLeast(r.level()))) {
            out.add(StudyType.LESEN);
        }
        if (catalog.listenings().values().stream().anyMatch(l -> working.isAtLeast(l.level()))) {
            out.add(StudyType.HOEREN);
        }
        if (catalog.writings().values().stream().anyMatch(w -> working.isAtLeast(w.level()))) {
            out.add(StudyType.SCHREIBEN);
        }
        if (catalog.speakings().values().stream().anyMatch(sp -> working.isAtLeast(sp.level()))) {
            out.add(StudyType.SPRECHEN);
        }
        return out;
    }

    /** Yerlestirme testinin en fazla suresi: sorularin ortalama estimatedMinutes'i x soru siniri. */
    private int placementMinutes() {
        List<Question> pool = new ArrayList<>();
        for (Level l : Level.assessable()) {
            pool.addAll(catalog.placementPool(l));
        }
        double avg = pool.stream().mapToInt(Question::estimatedMinutes).average().orElse(1);
        return (int) Math.ceil(avg * placement.maxQuestions());
    }

    private static List<Day> habit(CoachInput in) {
        List<Day> out = new ArrayList<>();
        for (int i = HABIT_DAYS - 1; i >= 0; i--) {
            LocalDate d = in.today().minusDays(i);
            Map<StudyType, Integer> m = new EnumMap<>(StudyType.class);
            m.putAll(WeeklyPlanner.minutesByType(in.activities(), d, d));
            out.add(new Day(d, m));
        }
        return out;
    }
}
