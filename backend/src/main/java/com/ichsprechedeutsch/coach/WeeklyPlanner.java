package com.ichsprechedeutsch.coach;

import com.ichsprechedeutsch.coach.CoachInput.DoneActivity;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.config.CoachProperties;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Haftalik ve gunluk plan (SPEC 5.4). Saf; bugun parametre.
 *
 * <ul>
 *   <li><b>Haftalik:</b> toplam sure (gunluk sure x haftalik gun), calisma
 *       seviyesinin paylarina gore turlere dagitilir. Aktif koc onerilerinin
 *       turleri pay kazanir (coach.recommendation-share-boost), paylar sonra
 *       yeniden 100'e olceklenir.</li>
 *   <li><b>Gunluk:</b> once oneriler (sureleriyle), kalan sure haftalik
 *       hedefte en cok eksigi olan turlere. Plan onerisidir, zorunlu degildir.</li>
 * </ul>
 */
public final class WeeklyPlanner {

    public record WeekItem(StudyType type, int sharePercent, int targetMinutes, int doneMinutes) {
        public int remainingMinutes() {
            return Math.max(0, targetMinutes - doneMinutes);
        }
    }

    public record WeeklyPlan(LocalDate weekStart, LocalDate weekEnd, int totalMinutes, Level level,
                             List<WeekItem> items, List<StudyType> boosted) {
    }

    /**
     * @param source RECOMMENDATION ya da WEEKLY
     * @param rule   oneriden geldiyse kural adi
     */
    public record DayItem(StudyType type, int minutes, String source, String rule, String hint) {
    }

    public record DailyPlan(LocalDate date, int totalMinutes, List<DayItem> items,
                            Map<StudyType, Integer> doneToday) {
    }

    private final CoachProperties config;

    public WeeklyPlanner(CoachProperties config) {
        this.config = config;
    }

    public WeeklyPlan weekly(CoachInput in, List<Recommendation> recommendations) {
        LocalDate start = in.today().with(TemporalAdjusters.previousOrSame(config.weekStart()));
        LocalDate end = start.plusDays(6);
        int total = in.dailyMinutes() * in.daysPerWeek();
        Level level = in.working().level();

        Set<StudyType> boosted = new LinkedHashSet<>();
        for (Recommendation r : recommendations) {
            if (r.type() != null) {
                boosted.add(r.type());
            }
        }

        Map<StudyType, Double> weights = new EnumMap<>(StudyType.class);
        double sum = 0;
        for (StudyType t : StudyType.values()) {
            double w = config.sharesFor(level).getOrDefault(t.key(), 0)
                    + (boosted.contains(t) ? config.recommendationShareBoost() : 0);
            weights.put(t, w);
            sum += w;
        }

        Map<StudyType, Integer> done = minutesByType(in.activities(), start, in.today());
        List<WeekItem> items = new ArrayList<>();
        for (StudyType t : StudyType.values()) {
            int share = (int) Math.round(weights.get(t) * 100 / sum);
            int target = roundDown((int) Math.round(total * weights.get(t) / sum));
            items.add(new WeekItem(t, share, target, done.getOrDefault(t, 0)));
        }
        items.sort((a, b) -> Integer.compare(b.sharePercent(), a.sharePercent()));
        return new WeeklyPlan(start, end, total, level, items, List.copyOf(boosted));
    }

    public DailyPlan daily(CoachInput in, List<Recommendation> recommendations, WeeklyPlan week) {
        int total = in.dailyMinutes();
        boolean comeback = recommendations.stream().anyMatch(r -> "COMEBACK".equals(r.rule()));
        if (comeback) {
            total = Math.max(config.planMinItemMinutes(),
                    roundDown((int) Math.round(total * config.comebackDayFraction())));
        }

        List<DayItem> items = new ArrayList<>();
        Map<StudyType, Integer> planned = new EnumMap<>(StudyType.class);
        int left = total;
        for (Recommendation r : recommendations) {
            if (r.type() == null || left < config.planMinItemMinutes()) {
                continue;
            }
            int m = Math.min(r.minutes(), left);
            items.add(new DayItem(r.type(), m, "RECOMMENDATION", r.rule(), r.action().hint()));
            planned.merge(r.type(), m, Integer::sum);
            left -= m;
        }

        // Kalan sure: haftalik hedefte en cok eksigi olan turler.
        while (left >= config.planMinItemMinutes()) {
            StudyType best = null;
            int bestGap = 0;
            for (WeekItem w : week.items()) {
                int gap = w.remainingMinutes() - planned.getOrDefault(w.type(), 0);
                if (gap > bestGap) {
                    best = w.type();
                    bestGap = gap;
                }
            }
            if (best == null) {
                // Haftalik hedef doldu: kalan sureyi paylara gore ekstra pratige ayir.
                best = week.items().getFirst().type();
                bestGap = left;
            }
            int m = roundDown(Math.min(bestGap, left));
            if (m < config.planMinItemMinutes()) {
                m = Math.min(left, config.planMinItemMinutes());
            }
            items.add(new DayItem(best, m, "WEEKLY", null,
                    RuleBasedCoach.hint(best, in.working().level())));
            planned.merge(best, m, Integer::sum);
            left -= m;
        }

        return new DailyPlan(in.today(), total, merge(items),
                minutesByType(in.activities(), in.today(), in.today()));
    }

    /** Ayni ture dusen ardisik kalemleri birlestirir (oneri kalemleri ayri kalir). */
    private static List<DayItem> merge(List<DayItem> items) {
        List<DayItem> out = new ArrayList<>();
        for (DayItem it : items) {
            int idx = -1;
            for (int i = 0; i < out.size(); i++) {
                DayItem o = out.get(i);
                if (o.type() == it.type() && "WEEKLY".equals(o.source()) && "WEEKLY".equals(it.source())) {
                    idx = i;
                }
            }
            if (idx >= 0) {
                DayItem o = out.get(idx);
                out.set(idx, new DayItem(o.type(), o.minutes() + it.minutes(), o.source(), null, o.hint()));
            } else {
                out.add(it);
            }
        }
        return out;
    }

    static Map<StudyType, Integer> minutesByType(List<DoneActivity> activities, LocalDate from, LocalDate to) {
        Map<StudyType, Integer> out = new EnumMap<>(StudyType.class);
        for (DoneActivity a : activities) {
            if (!a.date().isBefore(from) && !a.date().isAfter(to)) {
                out.merge(a.type(), a.minutes(), Integer::sum);
            }
        }
        return out;
    }

    private int roundDown(int minutes) {
        int b = config.planBlockMinutes();
        return (minutes / b) * b;
    }
}
