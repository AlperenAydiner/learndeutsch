package com.ichsprechedeutsch.progress;

import com.ichsprechedeutsch.progress.api.ProgressResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gelisim paneli.
 *
 * Buradaki sayilarin hepsi olculmus veridir; hicbiri tahmin degil.
 * Icgoruler de uydurulmaz: her biri belirli bir esige bagli ve o esik
 * asilmadiginda gosterilmez. Kullaniciya "harika gidiyorsun" demek
 * kolay ama degersizdir; "son 7 gunde Dativ'de 10 sorudan 3'unu dogru
 * yaptin" ise uzerine is yapilabilir bir bilgidir.
 */
@Service
public class ProgressService {

    /** Bir kategori hakkinda konusmak icin gereken en az soru sayisi. */
    private static final int MIN_ATTEMPTS_FOR_INSIGHT = 5;

    private final JdbcTemplate jdbc;

    public ProgressService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ProgressResponse load(UUID userId) {
        ProgressResponse.Plan plan = planSummary(userId);
        ProgressResponse.Adherence adherence = adherence(userId);
        ProgressResponse.Vocabulary vocabulary = vocabulary(userId);
        List<ProgressResponse.CategoryStat> categories = categories(userId);
        List<ProgressResponse.ScorePoint> scores = scores(userId);
        List<ProgressResponse.DayActivity> activity = activity(userId);

        return new ProgressResponse(
                plan, adherence, vocabulary, categories, scores, activity,
                skillEstimates(categories, vocabulary),
                insights(plan, adherence, vocabulary, categories));
    }

    // ------------------------------------------------------------------

    private ProgressResponse.Plan planSummary(UUID userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.start_date, p.end_date, p.total_days, p.feasibility,
                       count(*) FILTER (WHERE d.date < current_date) AS elapsed,
                       count(*) FILTER (WHERE d.status = 'DONE') AS done_days
                FROM plan p
                JOIN plan_day d ON d.plan_id = p.id
                WHERE p.user_id = ? AND p.status = 'ACTIVE'
                GROUP BY p.start_date, p.end_date, p.total_days, p.feasibility
                """, userId);

        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> r = rows.get(0);
        LocalDate end = toDate(r.get("end_date"));
        int total = (Integer) r.get("total_days");
        long elapsed = ((Number) r.get("elapsed")).longValue();

        return new ProgressResponse.Plan(
                toDate(r.get("start_date")), end, total,
                (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.now(), end)),
                (int) elapsed,
                ((Number) r.get("done_days")).intValue(),
                (String) r.get("feasibility"));
    }

    private ProgressResponse.Adherence adherence(UUID userId) {
        Map<String, Object> r = jdbc.queryForList("""
                SELECT
                    count(*) FILTER (WHERE tc.status = 'DONE') AS done,
                    count(*) FILTER (WHERE tc.status = 'PARTIAL') AS partial,
                    count(*) FILTER (WHERE tc.status = 'MISSED') AS missed,
                    count(*) FILTER (WHERE tc.status IS NULL AND d.date < current_date)
                        AS skipped,
                    count(*) FILTER (WHERE d.date <= current_date) AS expected
                FROM task t
                JOIN plan_day d ON d.id = t.plan_day_id
                JOIN plan p ON p.id = d.plan_id
                LEFT JOIN task_completion tc ON tc.task_id = t.id
                WHERE p.user_id = ? AND p.status = 'ACTIVE'
                """, userId).get(0);

        int done = num(r.get("done"));
        int partial = num(r.get("partial"));
        int missed = num(r.get("missed"));
        int skipped = num(r.get("skipped"));
        int expected = Math.max(1, num(r.get("expected")));

        // Yarim yapilan gorev yarim sayilir.
        double rate = (done + partial * 0.5) / expected;

        return new ProgressResponse.Adherence(done, partial, missed, skipped,
                expected, round2(rate), streak(userId));
    }

    private ProgressResponse.Vocabulary vocabulary(UUID userId) {
        Map<String, Object> r = jdbc.queryForList("""
                SELECT
                    count(*) AS total,
                    count(*) FILTER (WHERE repetition > 0) AS started,
                    count(*) FILTER (WHERE state = 'REVIEW') AS mastered,
                    count(*) FILTER (WHERE due_date <= current_date) AS due
                FROM user_word WHERE user_id = ?
                """, userId).get(0);

        Map<String, Object> log = jdbc.queryForList("""
                SELECT count(*) AS reviews,
                       count(*) FILTER (WHERE l.correct) AS correct
                FROM word_review_log l
                JOIN user_word uw ON uw.id = l.user_word_id
                WHERE uw.user_id = ?
                """, userId).get(0);

        int reviews = num(log.get("reviews"));
        int correct = num(log.get("correct"));

        return new ProgressResponse.Vocabulary(
                num(r.get("total")), num(r.get("started")), num(r.get("mastered")),
                num(r.get("due")), reviews,
                reviews == 0 ? 0 : round2((double) correct / reviews));
    }

    private List<ProgressResponse.CategoryStat> categories(UUID userId) {
        return jdbc.queryForList("""
                SELECT mc.code, mc.name_tr, mc.skill, s.correct, s.attempts
                FROM user_category_stat s
                JOIN mistake_category mc ON mc.id = s.mistake_category_id
                WHERE s.user_id = ? AND s.attempts > 0
                ORDER BY s.correct::numeric / s.attempts, mc.name_tr
                """, userId).stream()
                .map(r -> {
                    int correct = num(r.get("correct"));
                    int attempts = num(r.get("attempts"));
                    return new ProgressResponse.CategoryStat(
                            (String) r.get("code"), (String) r.get("name_tr"),
                            (String) r.get("skill"), correct, attempts,
                            round2((double) correct / attempts));
                })
                .toList();
    }

    private List<ProgressResponse.ScorePoint> scores(UUID userId) {
        return jdbc.queryForList("""
                SELECT ta.finished_at::date AS day, t.title_tr, t.test_type,
                       ta.score, ta.max_score
                FROM test_attempt ta
                JOIN test t ON t.id = ta.test_id
                WHERE ta.user_id = ? AND ta.status = 'COMPLETED' AND ta.max_score > 0
                ORDER BY ta.finished_at
                """, userId).stream()
                .map(r -> new ProgressResponse.ScorePoint(
                        toDate(r.get("day")),
                        (String) r.get("title_tr"),
                        (String) r.get("test_type"),
                        round2(((Number) r.get("score")).doubleValue()
                                / ((Number) r.get("max_score")).doubleValue())))
                .toList();
    }

    /** Son 30 gunun calisma yogunlugu. */
    private List<ProgressResponse.DayActivity> activity(UUID userId) {
        return jdbc.queryForList("""
                SELECT activity_date, tasks_done, minutes
                FROM user_activity
                WHERE user_id = ? AND activity_date >= current_date - 29
                ORDER BY activity_date
                """, userId).stream()
                .map(r -> new ProgressResponse.DayActivity(
                        toDate(r.get("activity_date")),
                        num(r.get("tasks_done")), num(r.get("minutes"))))
                .toList();
    }

    /**
     * Beceri bazli seviye tahmini.
     *
     * Kaba bir tahmin ve oyle sunulmali: yeterli veri yoksa "yetersiz veri"
     * denir, uydurma bir seviye yazilmaz.
     */
    private List<ProgressResponse.SkillEstimate> skillEstimates(
            List<ProgressResponse.CategoryStat> categories,
            ProgressResponse.Vocabulary vocabulary) {

        List<ProgressResponse.SkillEstimate> out = new ArrayList<>();

        for (String skill : List.of("GRAMMAR", "VOCAB", "READING")) {
            List<ProgressResponse.CategoryStat> group = categories.stream()
                    .filter(c -> skill.equals(c.skill()))
                    .toList();

            int attempts = group.stream().mapToInt(ProgressResponse.CategoryStat::attempts).sum();
            int correct = group.stream().mapToInt(ProgressResponse.CategoryStat::correct).sum();

            if (attempts < MIN_ATTEMPTS_FOR_INSIGHT) {
                out.add(new ProgressResponse.SkillEstimate(skill, null, 0, attempts));
                continue;
            }

            double ratio = (double) correct / attempts;
            String level = ratio >= 0.8 ? "A2+" : ratio >= 0.6 ? "A2" : ratio >= 0.4 ? "A1+" : "A1";
            out.add(new ProgressResponse.SkillEstimate(skill, level, round2(ratio), attempts));
        }
        return out;
    }

    /**
     * Otomatik icgoruler.
     *
     * Her biri bir esige bagli; esik asilmazsa o icgoru hic gosterilmez.
     * Bos yere cesaretlendirmek yerine susmak daha durust.
     */
    private List<String> insights(ProgressResponse.Plan plan,
                                  ProgressResponse.Adherence adherence,
                                  ProgressResponse.Vocabulary vocabulary,
                                  List<ProgressResponse.CategoryStat> categories) {
        List<String> out = new ArrayList<>();

        if (adherence.streak() >= 3) {
            out.add(adherence.streak() + " gündür ara vermedin.");
        }

        if (adherence.expectedTasks() >= 10 && adherence.rate() < 0.7) {
            out.add(String.format(
                    "Programın %%%d gerisindesin. Ayarlar'dan süreyi uzatmak veya "
                            + "günlük süreni düşürmek planı gerçekçi kılabilir.",
                    Math.round((1 - adherence.rate()) * 100)));
        }

        categories.stream()
                .filter(c -> c.attempts() >= MIN_ATTEMPTS_FOR_INSIGHT && c.ratio() < 0.5)
                .limit(1)
                .forEach(c -> out.add(String.format(
                        "%s konusunda %d sorudan %d'sini doğru yaptın. "
                                + "Program bu konuya daha fazla yer açacak.",
                        c.nameTr(), c.attempts(), c.correct())));

        if (vocabulary.dueReviews() >= 50) {
            out.add(vocabulary.dueReviews() + " kelime tekrarı birikmiş. "
                    + "Yeni kelime almadan önce onları bitirmen gerekiyor.");
        }

        if (vocabulary.totalReviews() >= 20 && vocabulary.successRate() >= 0.85) {
            out.add(String.format("Kelime tekrarlarında %%%d başarı: aralıklar uzuyor, "
                    + "her gün daha az kart göreceksin.",
                    Math.round(vocabulary.successRate() * 100)));
        }

        if (plan != null && "UNREALISTIC".equals(plan.feasibility())) {
            out.add("Hedefin mevcut süreye sığmıyor. Programı yeniden hesaplaman iyi olur.");
        }

        return out;
    }

    private int streak(UUID userId) {
        List<LocalDate> days = jdbc.queryForList("""
                SELECT activity_date FROM user_activity
                WHERE user_id = ? AND tasks_done > 0
                ORDER BY activity_date DESC LIMIT 400
                """, userId).stream().map(r -> toDate(r.get("activity_date"))).toList();

        if (days.isEmpty()) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        LocalDate expected = days.get(0).equals(today) ? today : today.minusDays(1);

        int streak = 0;
        for (LocalDate day : days) {
            if (day.equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (day.isBefore(expected)) {
                break;
            }
        }
        return streak;
    }

    // ------------------------------------------------------------------

    private int num(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private LocalDate toDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        return value == null ? null : ((java.sql.Date) value).toLocalDate();
    }
}
