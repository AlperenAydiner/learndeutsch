package com.ichsprechedeutsch.plan;

import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.goal.LearningGoal;
import com.ichsprechedeutsch.goal.LearningGoalService;
import com.ichsprechedeutsch.plan.generator.BlockSpec;
import com.ichsprechedeutsch.plan.generator.GeneratedPlan;
import com.ichsprechedeutsch.plan.generator.PlanGenerator;
import com.ichsprechedeutsch.plan.generator.PlanRequest;
import com.ichsprechedeutsch.plan.generator.PlannedDay;
import com.ichsprechedeutsch.plan.generator.PlannedTask;
import com.ichsprechedeutsch.plan.api.PlanDayResponse;
import com.ichsprechedeutsch.plan.api.PlanResponse;
import com.ichsprechedeutsch.plan.api.TaskResponse;
import com.ichsprechedeutsch.plan.generator.UnitSpec;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Program ureticiyi veritabanina baglar.
 *
 * Uretici saf kalsin diye butun okuma ve yazma burada olur: icerik
 * havuzunu okur, saf kayitlara cevirir, ureticiyi cagirir, sonucu
 * kaydeder. Uretici hicbir zaman veritabani gormez.
 */
@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    /** user_category_stat'ta bu oranin ustu "biliniyor" sayilir. */
    private static final double MASTERED = 0.80;

    /** Bu oranin ustu "yarim biliniyor". */
    private static final double PARTIAL = 0.50;

    private final JdbcTemplate jdbc;
    private final LearningGoalService goalService;
    private final PlanGenerator generator = new PlanGenerator();

    public PlanService(JdbcTemplate jdbc, LearningGoalService goalService) {
        this.jdbc = jdbc;
        this.goalService = goalService;
    }

    // ------------------------------------------------------------------

    /** Aktif hedefe gore yeni plan uretir ve kaydeder. */
    @Transactional
    public UUID generateForUser(UUID userId) {
        LearningGoal goal = goalService.activeGoal(userId);
        PlanRequest request = buildRequest(userId, goal);
        GeneratedPlan generated = generator.generate(request);

        // Ayni anda tek aktif plan: eskisi yeniden hesaplanmis sayilir.
        jdbc.update("""
                UPDATE plan SET status = 'RECALCULATED'
                WHERE user_id = ? AND status = 'ACTIVE'
                """, userId);

        int version = nextVersion(userId);
        UUID planId = insertPlan(userId, goal, generated, version);
        persistDays(planId, generated);

        log.info("Plan uretildi: kullanici={} gun={} fizibilite={} atlanan={} sigmayan={}",
                userId, generated.days().size(), generated.verdict().feasibility(),
                generated.skippedUnits(), generated.unplacedUnits());

        return planId;
    }

    // ------------------------------------------------------------------
    // Girdi toplama
    // ------------------------------------------------------------------

    private PlanRequest buildRequest(UUID userId, LearningGoal goal) {
        List<UnitSpec> units = loadUnits();
        Map<String, List<BlockSpec>> blocks = loadBlocks();
        Map<String, Double> ratios = loadCategoryRatios(userId);

        Set<String> mastered = new LinkedHashSet<>();
        Set<String> partial = new LinkedHashSet<>();
        ratios.forEach((code, ratio) -> {
            if (ratio >= MASTERED) {
                mastered.add(code);
            } else if (ratio >= PARTIAL) {
                partial.add(code);
            }
        });

        return new PlanRequest(
                goal.getTargetLevel(), goal.getExamType(), goal.getTotalDays(),
                goal.getDailyMinutes(), goal.getStartDate(),
                units, blocks, mastered, partial, loadWeakTopics(userId));
    }

    private List<UnitSpec> loadUnits() {
        Map<UUID, List<String>> categoriesByUnit = new HashMap<>();
        jdbc.queryForList("""
                SELECT cc.content_unit_id, mc.code
                FROM content_unit_category cc
                JOIN mistake_category mc ON mc.id = cc.mistake_category_id
                """).forEach(row -> categoriesByUnit
                .computeIfAbsent((UUID) row.get("content_unit_id"), k -> new ArrayList<>())
                .add((String) row.get("code")));

        return jdbc.queryForList("""
                SELECT id, code, level, phase, sequence_no, title_tr,
                       grammar_summary, estimated_minutes, is_new_content
                FROM content_unit
                ORDER BY sequence_no
                """).stream()
                .map(row -> {
                    UUID id = (UUID) row.get("id");
                    return new UnitSpec(
                            id,
                            (String) row.get("code"),
                            (String) row.get("level"),
                            (String) row.get("phase"),
                            (Integer) row.get("sequence_no"),
                            (String) row.get("title_tr"),
                            (String) row.get("grammar_summary"),
                            (Integer) row.get("estimated_minutes"),
                            (Boolean) row.get("is_new_content"),
                            categoriesByUnit.getOrDefault(id, List.of()));
                })
                .toList();
    }

    /**
     * Blok sirasi onemlidir: gun asiri rotasyon blogun sirasina bakar.
     * Referans sablondaki dogal sirayi (kelime, gramer, dinleme, ...)
     * task_type uzerinden sabitliyoruz ki plan her uretimde ayni ciksin.
     */
    private Map<String, List<BlockSpec>> loadBlocks() {
        Map<String, List<BlockSpec>> out = new LinkedHashMap<>();

        jdbc.queryForList("""
                SELECT cu.code AS unit_code, b.task_type, b.reference_minutes,
                       b.instruction_tr,
                       CASE b.task_type
                           WHEN 'KELIME' THEN 1
                           WHEN 'GRAMER' THEN 2
                           WHEN 'HOEREN' THEN 3
                           WHEN 'LESEN' THEN 4
                           WHEN 'SCHREIBEN' THEN 5
                           WHEN 'SPRECHEN' THEN 6
                           ELSE 7
                       END AS ordering
                FROM content_unit_block b
                JOIN content_unit cu ON cu.id = b.content_unit_id
                ORDER BY cu.sequence_no, ordering
                """).forEach(row -> out
                .computeIfAbsent((String) row.get("unit_code"), k -> new ArrayList<>())
                .add(new BlockSpec(
                        (String) row.get("task_type"),
                        (Integer) row.get("reference_minutes"),
                        (String) row.get("instruction_tr"))));

        return out;
    }

    /**
     * En zayif konularin Turkce adlari, kotuden iyiye sirali.
     * Hafif gunlere ad vermek icin kullanilir: "Tekrar gunu" demek yerine
     * "Tekrar: Dativ, Perfekt" demek kullaniciya ne yapacagini soyler.
     */
    private List<String> loadWeakTopics(UUID userId) {
        return jdbc.queryForList("""
                SELECT mc.name_tr
                FROM user_category_stat s
                JOIN mistake_category mc ON mc.id = s.mistake_category_id
                WHERE s.user_id = ? AND s.attempts > 0
                ORDER BY s.correct::numeric / s.attempts ASC, mc.name_tr
                LIMIT 5
                """, String.class, userId);
    }

    /** Kategori bazli dogruluk orani; yerlestirme ve quiz'lerden birikir. */
    private Map<String, Double> loadCategoryRatios(UUID userId) {
        Map<String, Double> out = new LinkedHashMap<>();
        jdbc.queryForList("""
                SELECT mc.code, s.correct, s.attempts
                FROM user_category_stat s
                JOIN mistake_category mc ON mc.id = s.mistake_category_id
                WHERE s.user_id = ? AND s.attempts > 0
                """, userId).forEach(row -> {
            int correct = (Integer) row.get("correct");
            int attempts = (Integer) row.get("attempts");
            out.put((String) row.get("code"), (double) correct / attempts);
        });
        return out;
    }

    // ------------------------------------------------------------------
    // Kaydetme
    // ------------------------------------------------------------------

    private int nextVersion(UUID userId) {
        Integer max = jdbc.queryForObject(
                "SELECT coalesce(max(version), 0) FROM plan WHERE user_id = ?",
                Integer.class, userId);
        return (max == null ? 0 : max) + 1;
    }

    private UUID insertPlan(UUID userId, LearningGoal goal,
                            GeneratedPlan generated, int version) {
        return jdbc.queryForObject("""
                INSERT INTO plan
                    (user_id, learning_goal_id, start_date, end_date, total_days,
                     daily_minutes, status, feasibility, version)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                RETURNING id
                """, UUID.class,
                userId, goal.getId(), goal.getStartDate(), goal.targetDate(),
                goal.getTotalDays(), goal.getDailyMinutes(),
                generated.verdict().feasibility().name(), version);
    }

    private void persistDays(UUID planId, GeneratedPlan generated) {
        List<Object[]> dayRows = new ArrayList<>();
        for (PlannedDay day : generated.days()) {
            dayRows.add(new Object[]{
                    planId, day.dayNumber(), day.date(), day.contentUnitId(),
                    day.dayType(), day.plannedMinutes()});
        }
        jdbc.batchUpdate("""
                INSERT INTO plan_day
                    (plan_id, day_number, date, content_unit_id, day_type, planned_minutes)
                VALUES (?, ?, ?, ?, ?, ?)
                """, dayRows);

        Map<Integer, UUID> dayIds = new HashMap<>();
        jdbc.queryForList("SELECT id, day_number FROM plan_day WHERE plan_id = ?", planId)
                .forEach(row -> dayIds.put(
                        (Integer) row.get("day_number"), (UUID) row.get("id")));

        List<Object[]> taskRows = new ArrayList<>();
        for (PlannedDay day : generated.days()) {
            UUID dayId = dayIds.get(day.dayNumber());
            for (PlannedTask task : day.tasks()) {
                taskRows.add(new Object[]{
                        dayId, task.taskType(), task.plannedMinutes(),
                        task.contentUnitId(), task.orderNo()});
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO task
                    (plan_day_id, task_type, planned_minutes, content_unit_id, order_no)
                VALUES (?, ?, ?, ?, ?)
                """, taskRows);
    }

    // ------------------------------------------------------------------


    /** Aktif planin tamami: gunler ve gorevler. */
    @Transactional(readOnly = true)
    public PlanResponse loadActivePlan(UUID userId) {
        List<Map<String, Object>> plans = jdbc.queryForList("""
                SELECT id, start_date, end_date, total_days, daily_minutes,
                       feasibility, version
                FROM plan
                WHERE user_id = ? AND status = 'ACTIVE'
                """, userId);

        if (plans.isEmpty()) {
            throw new NotFoundException("Henuz bir programin yok");
        }
        Map<String, Object> plan = plans.get(0);
        UUID planId = (UUID) plan.get("id");

        return new PlanResponse(
                planId,
                toLocalDate(plan.get("start_date")),
                toLocalDate(plan.get("end_date")),
                (Integer) plan.get("total_days"),
                (Integer) plan.get("daily_minutes"),
                (String) plan.get("feasibility"),
                (Integer) plan.get("version"),
                loadDays(planId));
    }

    private List<PlanDayResponse> loadDays(UUID planId) {
        Map<UUID, List<TaskResponse>> tasksByDay = new LinkedHashMap<>();
        jdbc.queryForList("""
                SELECT t.id, t.plan_day_id, t.task_type, t.planned_minutes, t.order_no,
                       b.instruction_tr, tc.status
                FROM task t
                JOIN plan_day d ON d.id = t.plan_day_id
                LEFT JOIN content_unit_block b
                       ON b.content_unit_id = t.content_unit_id
                      AND b.task_type = t.task_type
                LEFT JOIN task_completion tc ON tc.task_id = t.id
                WHERE d.plan_id = ?
                ORDER BY d.day_number, t.order_no
                """, planId).forEach(row -> tasksByDay
                .computeIfAbsent((UUID) row.get("plan_day_id"), k -> new ArrayList<>())
                .add(new TaskResponse(
                        (UUID) row.get("id"),
                        (String) row.get("task_type"),
                        (Integer) row.get("planned_minutes"),
                        ((Number) row.get("order_no")).intValue(),
                        (String) row.get("instruction_tr"),
                        (String) row.get("status"))));

        return jdbc.queryForList("""
                SELECT d.id, d.day_number, d.date, d.day_type, d.planned_minutes, d.status,
                       cu.code AS unit_code, cu.title_tr, cu.grammar_summary, cu.vocab_theme
                FROM plan_day d
                LEFT JOIN content_unit cu ON cu.id = d.content_unit_id
                WHERE d.plan_id = ?
                ORDER BY d.day_number
                """, planId).stream()
                .map(row -> {
                    UUID dayId = (UUID) row.get("id");
                    return new PlanDayResponse(
                            dayId,
                            (Integer) row.get("day_number"),
                            toLocalDate(row.get("date")),
                            (String) row.get("day_type"),
                            (String) row.get("unit_code"),
                            (String) row.get("title_tr"),
                            (String) row.get("grammar_summary"),
                            (String) row.get("vocab_theme"),
                            (Integer) row.get("planned_minutes"),
                            (String) row.get("status"),
                            tasksByDay.getOrDefault(dayId, List.of()));
                })
                .toList();
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        return value == null ? null : ((java.sql.Date) value).toLocalDate();
    }

    /** Kullanicinin aktif planinin kimligi. */
    @Transactional(readOnly = true)
    public UUID activePlanId(UUID userId) {
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM plan WHERE user_id = ? AND status = 'ACTIVE'",
                UUID.class, userId);
        if (ids.isEmpty()) {
            throw new NotFoundException("Henuz bir programin yok");
        }
        return ids.get(0);
    }
}
