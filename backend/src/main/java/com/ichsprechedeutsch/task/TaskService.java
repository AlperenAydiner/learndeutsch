package com.ichsprechedeutsch.task;

import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.task.api.CompleteTaskRequest;
import com.ichsprechedeutsch.task.api.TodayResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gunun gorevleri: gosterme ve isaretleme.
 *
 * Bir gorevi isaretlemek birkac saniye surmelidir. O yuzden tek istekte
 * hem durum hem oz bildirim gonderilir ve yanitta gunun guncel ilerlemesi
 * doner - istemci ikinci bir istek atmak zorunda kalmaz.
 */
@Service
public class TaskService {

    private final JdbcTemplate jdbc;

    public TaskService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    // Okuma
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TodayResponse today(UUID userId) {
        return dayFor(userId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public TodayResponse dayFor(UUID userId, LocalDate date) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.id, d.day_number, d.date, d.day_type, d.planned_minutes, d.status,
                       p.total_days, p.end_date,
                       cu.title_tr, cu.grammar_summary, cu.vocab_theme
                FROM plan_day d
                JOIN plan p ON p.id = d.plan_id
                LEFT JOIN content_unit cu ON cu.id = d.content_unit_id
                WHERE p.user_id = ? AND p.status = 'ACTIVE' AND d.date = ?
                """, userId, date);

        if (rows.isEmpty()) {
            throw new NotFoundException(outOfRangeMessage(userId, date));
        }

        Map<String, Object> day = rows.get(0);
        UUID dayId = (UUID) day.get("id");
        List<TodayResponse.Task> tasks = loadTasks(dayId);

        return new TodayResponse(
                dayId,
                (Integer) day.get("day_number"),
                (Integer) day.get("total_days"),
                date,
                toLocalDate(day.get("end_date")),
                (String) day.get("day_type"),
                (String) day.get("title_tr"),
                (String) day.get("grammar_summary"),
                (String) day.get("vocab_theme"),
                (Integer) day.get("planned_minutes"),
                (String) day.get("status"),
                tasks,
                progress(tasks),
                streak(userId));
    }

    private List<TodayResponse.Task> loadTasks(UUID dayId) {
        return jdbc.queryForList("""
                SELECT t.id, t.task_type, t.planned_minutes, t.order_no,
                       b.instruction_tr, tc.status, tc.self_report::text AS self_report
                FROM task t
                LEFT JOIN content_unit_block b
                       ON b.content_unit_id = t.content_unit_id
                      AND b.task_type = t.task_type
                LEFT JOIN task_completion tc ON tc.task_id = t.id
                WHERE t.plan_day_id = ?
                ORDER BY t.order_no
                """, dayId).stream()
                .map(row -> new TodayResponse.Task(
                        (UUID) row.get("id"),
                        (String) row.get("task_type"),
                        (Integer) row.get("planned_minutes"),
                        ((Number) row.get("order_no")).intValue(),
                        (String) row.get("instruction_tr"),
                        (String) row.get("status"),
                        (String) row.get("self_report"),
                        fieldsFor((String) row.get("task_type"))))
                .toList();
    }

    /** Arayuz hangi secenekleri gosterecegini sunucudan ogrenir. */
    private List<TodayResponse.ReportField> fieldsFor(String taskType) {
        List<TodayResponse.ReportField> out = new ArrayList<>();
        for (SelfReportSpec.Field field : SelfReportSpec.fieldsFor(taskType)) {
            out.add(new TodayResponse.ReportField(
                    field.name(), field.allowed(), field.numeric()));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Yazma
    // ------------------------------------------------------------------

    @Transactional
    public TodayResponse complete(UUID userId, UUID taskId, CompleteTaskRequest request) {
        SelfReportSpec.validateStatus(request.status());

        Map<String, Object> task = findOwnedTask(userId, taskId);
        String taskType = (String) task.get("task_type");
        LocalDate date = toLocalDate(task.get("date"));

        String json = SelfReportSpec.toJson(taskType, request.selfReport());

        jdbc.update("""
                INSERT INTO task_completion
                    (task_id, user_id, status, self_report, completed_at)
                VALUES (?, ?, ?, ?::jsonb, now())
                ON CONFLICT (task_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    self_report = EXCLUDED.self_report,
                    completed_at = now()
                """, taskId, userId, request.status(), json);

        UUID dayId = (UUID) task.get("plan_day_id");
        refreshDayStatus(dayId);
        refreshActivity(userId, date);

        return dayFor(userId, date);
    }

    /**
     * Gorev gercekten bu kullaniciya mi ait?
     *
     * Kimlik token'dan gelir ama gorev kimligi istekten gelir. Bu kontrol
     * olmadan bir kullanici baskasinin gorevini isaretleyebilirdi.
     */
    private Map<String, Object> findOwnedTask(UUID userId, UUID taskId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.id, t.task_type, t.plan_day_id, d.date
                FROM task t
                JOIN plan_day d ON d.id = t.plan_day_id
                JOIN plan p ON p.id = d.plan_id
                WHERE t.id = ? AND p.user_id = ?
                """, taskId, userId);

        if (rows.isEmpty()) {
            throw new NotFoundException("Gorev bulunamadi");
        }
        return rows.get(0);
    }

    /** Gunun durumu gorevlerinden turetilir; elle tutulmaz. */
    private void refreshDayStatus(UUID dayId) {
        jdbc.update("""
                UPDATE plan_day d
                SET status = sub.new_status
                FROM (
                    SELECT t.plan_day_id,
                           CASE
                               WHEN count(*) FILTER (WHERE tc.status IS NULL) = 0
                                    AND count(*) FILTER (WHERE tc.status = 'MISSED') = count(*)
                                   THEN 'MISSED'
                               WHEN count(*) FILTER (WHERE tc.status IS NULL) = 0
                                   THEN 'DONE'
                               WHEN count(*) FILTER (WHERE tc.status IS NOT NULL) > 0
                                   THEN 'IN_PROGRESS'
                               ELSE 'PENDING'
                           END AS new_status
                    FROM task t
                    LEFT JOIN task_completion tc ON tc.task_id = t.id
                    WHERE t.plan_day_id = ?
                    GROUP BY t.plan_day_id
                ) sub
                WHERE d.id = sub.plan_day_id
                """, dayId);
    }

    /**
     * Gunluk ozet: seri (streak) hesabi bunun uzerinden yapilir.
     * Yarim yapilan gorev de calisma sayilir - sifir gun ile yarim gun
     * arasindaki fark, aliskanligin kendisidir.
     */
    private void refreshActivity(UUID userId, LocalDate date) {
        jdbc.update("""
                INSERT INTO user_activity (user_id, activity_date, tasks_done, minutes)
                SELECT ?, ?,
                       count(*) FILTER (WHERE tc.status IN ('DONE', 'PARTIAL')),
                       coalesce(sum(t.planned_minutes)
                                FILTER (WHERE tc.status = 'DONE'), 0)
                     + coalesce(sum(t.planned_minutes / 2)
                                FILTER (WHERE tc.status = 'PARTIAL'), 0)
                FROM task t
                JOIN plan_day d ON d.id = t.plan_day_id
                JOIN plan p ON p.id = d.plan_id
                LEFT JOIN task_completion tc ON tc.task_id = t.id
                WHERE p.user_id = ? AND d.date = ?
                ON CONFLICT (user_id, activity_date) DO UPDATE SET
                    tasks_done = EXCLUDED.tasks_done,
                    minutes = EXCLUDED.minutes
                """, userId, date, userId, date);
    }

    // ------------------------------------------------------------------

    private TodayResponse.Progress progress(List<TodayResponse.Task> tasks) {
        int done = 0;
        int partial = 0;
        int missed = 0;
        int doneMinutes = 0;
        int totalMinutes = 0;

        for (TodayResponse.Task task : tasks) {
            totalMinutes += task.plannedMinutes();
            if (task.status() == null) {
                continue;
            }
            switch (task.status()) {
                case "DONE" -> {
                    done++;
                    doneMinutes += task.plannedMinutes();
                }
                case "PARTIAL" -> {
                    partial++;
                    doneMinutes += task.plannedMinutes() / 2;
                }
                case "MISSED" -> missed++;
                default -> { }
            }
        }
        return new TodayResponse.Progress(
                done, partial, missed, tasks.size(), doneMinutes, totalMinutes);
    }

    /**
     * Ust uste calisilan gun sayisi.
     *
     * Bugun henuz calisilmadiysa seri kirilmis sayilmaz: gun bitmeden
     * kimseye "serini kaybettin" demek haksizlik olur. Sayim dunden geriye
     * gider, bugun calisildiysa bir eklenir.
     */
    private int streak(UUID userId) {
        List<LocalDate> days = jdbc.queryForList("""
                SELECT activity_date FROM user_activity
                WHERE user_id = ? AND tasks_done > 0
                ORDER BY activity_date DESC
                LIMIT 400
                """, userId).stream()
                .map(row -> toLocalDate(row.get("activity_date")))
                .toList();

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

    private String outOfRangeMessage(UUID userId, LocalDate date) {
        List<Map<String, Object>> plan = jdbc.queryForList("""
                SELECT start_date, end_date FROM plan
                WHERE user_id = ? AND status = 'ACTIVE'
                """, userId);

        if (plan.isEmpty()) {
            return "Henuz bir programin yok";
        }
        LocalDate start = toLocalDate(plan.get(0).get("start_date"));
        LocalDate end = toLocalDate(plan.get(0).get("end_date"));

        if (date.isBefore(start)) {
            return "Programin " + start + " tarihinde basliyor";
        }
        if (date.isAfter(end)) {
            return "Programin " + end + " tarihinde bitti";
        }
        return "Bu gun programda bulunamadi";
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        return value == null ? null : ((java.sql.Date) value).toLocalDate();
    }
}
