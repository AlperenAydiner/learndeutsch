package com.ichsprechedeutsch.placement;

import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.placement.api.PlacementResultResponse;
import com.ichsprechedeutsch.placement.api.PlacementSubmitRequest;
import com.ichsprechedeutsch.placement.api.PlacementTestResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Yerlestirme testi: sorulari sunar, cevaplari puanlar, sonucu kaydeder.
 *
 * Bu servisin ciktisi program ureticinin girdisidir: hangi konularin
 * atlanacagi buradaki kategori sonuclarindan belirlenir.
 *
 * JPA yerine JdbcTemplate kullaniliyor. Kural su: kullanicinin sahip
 * oldugu, zamanla degisen kayitlar (AppUser, LearningGoal, ileride Plan)
 * JPA ile; cok tabloyu birlestirip okuyan ve toplu yazan islemler
 * (icerik, olcme) JdbcTemplate ile yonetilir.
 */
@Service
public class PlacementService {

    private static final String PLACEMENT_TEST_CODE = "PLACEMENT";

    /** Bir kategori bu oranin uzerindeyse konu biliniyor sayilir ve atlanir. */
    private static final double MASTERED = 0.80;

    /** Bu oranin uzerindeyse kismen biliniyor: sure yariya iner. */
    private static final double PARTIAL = 0.50;

    /** A2 sorularinda bu oranin uzerindeyse seviye A2 tahmin edilir. */
    private static final double A2_THRESHOLD = 0.60;

    private final JdbcTemplate jdbc;

    public PlacementService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PlacementTestResponse loadTest() {
        Map<String, Object> test = findTest();
        UUID testId = (UUID) test.get("id");

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT q.id AS question_id, q.question_type, q.prompt_de,
                       o.id AS option_id, o.option_text, tq.order_no, o.order_no AS opt_order
                FROM test_question tq
                JOIN question q ON q.id = tq.question_id
                JOIN question_option o ON o.question_id = q.id
                WHERE tq.test_id = ?
                ORDER BY tq.order_no, o.order_no
                """, testId);

        // Soru sirasi korunsun diye LinkedHashMap.
        Map<UUID, List<PlacementTestResponse.OptionView>> optionsByQuestion =
                new LinkedHashMap<>();
        Map<UUID, Map<String, Object>> questionInfo = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            UUID questionId = (UUID) row.get("question_id");
            questionInfo.putIfAbsent(questionId, row);
            optionsByQuestion
                    .computeIfAbsent(questionId, k -> new ArrayList<>())
                    .add(new PlacementTestResponse.OptionView(
                            (UUID) row.get("option_id"),
                            (String) row.get("option_text")));
        }

        List<PlacementTestResponse.QuestionView> questions = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, Object>> e : questionInfo.entrySet()) {
            questions.add(new PlacementTestResponse.QuestionView(
                    e.getKey(),
                    (String) e.getValue().get("question_type"),
                    (String) e.getValue().get("prompt_de"),
                    optionsByQuestion.get(e.getKey())));
        }

        return new PlacementTestResponse(
                testId,
                (String) test.get("title_tr"),
                (Integer) test.get("time_limit_minutes"),
                questions);
    }

    // ------------------------------------------------------------------

    @Transactional
    public PlacementResultResponse submit(UUID userId, PlacementSubmitRequest request) {
        Map<String, Object> test = findTest();
        UUID testId = (UUID) test.get("id");

        Map<UUID, QuestionKey> keys = loadAnswerKey(testId);

        UUID attemptId = jdbc.queryForObject("""
                INSERT INTO test_attempt (user_id, test_id, status)
                VALUES (?, ?, 'IN_PROGRESS')
                RETURNING id
                """, UUID.class, userId, testId);

        List<Object[]> answerRows = new ArrayList<>();
        Map<String, int[]> perCategory = new LinkedHashMap<>();   // kod -> [dogru, toplam]
        Map<String, int[]> perSkill = new LinkedHashMap<>();
        Map<String, int[]> perLevel = new LinkedHashMap<>();
        int correct = 0;

        for (PlacementSubmitRequest.Answer answer : request.answers()) {
            QuestionKey key = keys.get(answer.questionId());
            if (key == null) {
                throw new ValidationException("Bu teste ait olmayan soru gonderildi");
            }

            boolean isCorrect = answer.selectedOptionId() != null
                    && answer.selectedOptionId().equals(key.correctOptionId());
            if (isCorrect) {
                correct++;
            }

            answerRows.add(new Object[]{
                    attemptId, answer.questionId(), answer.selectedOptionId(), isCorrect});

            tally(perCategory, key.categoryCode(), isCorrect);
            tally(perSkill, key.skill(), isCorrect);
            tally(perLevel, key.level(), isCorrect);
        }

        jdbc.batchUpdate("""
                INSERT INTO attempt_answer
                    (attempt_id, question_id, selected_option_id, is_correct)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (attempt_id, question_id) DO UPDATE SET
                    selected_option_id = EXCLUDED.selected_option_id,
                    is_correct = EXCLUDED.is_correct
                """, answerRows);

        int total = request.answers().size();
        jdbc.update("""
                UPDATE test_attempt
                SET status = 'COMPLETED', finished_at = now(), score = ?, max_score = ?
                WHERE id = ?
                """, (double) correct, (double) total, attemptId);

        List<PlacementResultResponse.CategoryResult> categories =
                buildCategoryResults(perCategory);
        List<String> mastered = categories.stream()
                .filter(c -> "MASTERED".equals(c.status()))
                .map(PlacementResultResponse.CategoryResult::code)
                .toList();

        String level = estimateLevel(perLevel);
        Map<String, Double> skillScores = ratios(perSkill);

        savePlacementResult(userId, attemptId, level, skillScores, mastered);
        updateCategoryStats(userId, perCategory);

        return new PlacementResultResponse(
                attemptId, level, correct, total, skillScores, categories, mastered,
                countSkippableUnits(mastered));
    }

    // ------------------------------------------------------------------

    private Map<String, Object> findTest() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, title_tr, time_limit_minutes FROM test WHERE code = ?",
                PLACEMENT_TEST_CODE);
        if (rows.isEmpty()) {
            throw new NotFoundException("Yerlestirme testi bulunamadi");
        }
        return rows.get(0);
    }

    private Map<UUID, QuestionKey> loadAnswerKey(UUID testId) {
        Map<UUID, QuestionKey> keys = new LinkedHashMap<>();
        jdbc.queryForList("""
                SELECT q.id, q.skill, q.level, mc.code AS category_code,
                       (SELECT o.id FROM question_option o
                        WHERE o.question_id = q.id AND o.is_correct LIMIT 1) AS correct_option_id
                FROM test_question tq
                JOIN question q ON q.id = tq.question_id
                JOIN mistake_category mc ON mc.id = q.mistake_category_id
                WHERE tq.test_id = ?
                """, testId).forEach(row -> keys.put(
                (UUID) row.get("id"),
                new QuestionKey(
                        (UUID) row.get("correct_option_id"),
                        (String) row.get("category_code"),
                        (String) row.get("skill"),
                        (String) row.get("level"))));
        return keys;
    }

    private void tally(Map<String, int[]> counts, String key, boolean isCorrect) {
        int[] c = counts.computeIfAbsent(key, k -> new int[2]);
        if (isCorrect) {
            c[0]++;
        }
        c[1]++;
    }

    private List<PlacementResultResponse.CategoryResult> buildCategoryResults(
            Map<String, int[]> perCategory) {

        Map<String, String> names = new LinkedHashMap<>();
        jdbc.queryForList("SELECT code, name_tr FROM mistake_category")
                .forEach(r -> names.put((String) r.get("code"), (String) r.get("name_tr")));

        List<PlacementResultResponse.CategoryResult> out = new ArrayList<>();
        perCategory.forEach((code, c) -> {
            double ratio = c[1] == 0 ? 0 : (double) c[0] / c[1];
            String status = ratio >= MASTERED ? "MASTERED"
                    : ratio >= PARTIAL ? "PARTIAL" : "WEAK";
            out.add(new PlacementResultResponse.CategoryResult(
                    code, names.getOrDefault(code, code), c[0], c[1], status));
        });
        // Zayiftan gucluye: kullanici once nerede eksigi oldugunu gormeli.
        out.sort((a, b) -> Double.compare(
                (double) a.correct() / a.total(), (double) b.correct() / b.total()));
        return out;
    }

    private String estimateLevel(Map<String, int[]> perLevel) {
        int[] a2 = perLevel.getOrDefault("A2", new int[2]);
        double a2Ratio = a2[1] == 0 ? 0 : (double) a2[0] / a2[1];
        return a2Ratio >= A2_THRESHOLD ? "A2" : "A1";
    }

    private Map<String, Double> ratios(Map<String, int[]> counts) {
        Map<String, Double> out = new LinkedHashMap<>();
        counts.forEach((k, c) -> out.put(k,
                c[1] == 0 ? 0.0 : Math.round((double) c[0] / c[1] * 100) / 100.0));
        return out;
    }

    private void savePlacementResult(UUID userId, UUID attemptId, String level,
                                     Map<String, Double> skillScores,
                                     List<String> mastered) {
        StringBuilder json = new StringBuilder("{");
        skillScores.forEach((k, v) -> {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"').append(k).append("\":").append(v);
        });
        json.append('}');

        // JdbcTemplate String[] parametresini PostgreSQL dizisine cevirmez;
        // virgulle birlestirip SQL tarafinda diziye donusturuyoruz.
        jdbc.update("""
                INSERT INTO placement_result
                    (user_id, test_attempt_id, estimated_level, skill_scores,
                     mastered_category_codes)
                VALUES (?, ?, ?, ?::jsonb,
                        COALESCE(string_to_array(NULLIF(?, ''), ','), '{}'))
                """,
                userId, attemptId, level, json.toString(),
                String.join(",", mastered));
    }

    /**
     * Yerlestirme sonucu adaptif motorun ilk verisidir: zayif kategoriler
     * daha yuksek agirlik alir ve program ureticide one cikar.
     */
    private void updateCategoryStats(UUID userId, Map<String, int[]> perCategory) {
        List<Object[]> batch = new ArrayList<>();
        perCategory.forEach((code, c) -> {
            double ratio = c[1] == 0 ? 0 : (double) c[0] / c[1];
            double weight = 1.0 + (1.0 - ratio) * 1.5;   // 1.00 (tam) .. 2.50 (hic)
            batch.add(new Object[]{userId, code, c[0], c[1], Math.round(weight * 100) / 100.0});
        });

        jdbc.batchUpdate("""
                INSERT INTO user_category_stat
                    (user_id, mistake_category_id, correct, attempts, weight, updated_at)
                SELECT ?, mc.id, ?, ?, ?, now()
                FROM mistake_category mc
                WHERE mc.code = ?
                ON CONFLICT (user_id, mistake_category_id) DO UPDATE SET
                    correct = user_category_stat.correct + EXCLUDED.correct,
                    attempts = user_category_stat.attempts + EXCLUDED.attempts,
                    weight = EXCLUDED.weight,
                    updated_at = now()
                """, batch.stream()
                .map(r -> new Object[]{r[0], r[2], r[3], r[4], r[1]})
                .toList());
    }

    /** Bilinen kategoriler yuzunden tamamen atlanabilecek birim sayisi. */
    private int countSkippableUnits(List<String> mastered) {
        if (mastered.isEmpty()) {
            return 0;
        }
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM content_unit cu
                WHERE cu.is_new_content
                  AND EXISTS (SELECT 1 FROM content_unit_category cc WHERE cc.content_unit_id = cu.id)
                  AND NOT EXISTS (
                      SELECT 1
                      FROM content_unit_category cc
                      JOIN mistake_category mc ON mc.id = cc.mistake_category_id
                      WHERE cc.content_unit_id = cu.id
                        AND mc.code <> ALL (string_to_array(?, ','))
                  )
                """, Integer.class, String.join(",", mastered));
        return n == null ? 0 : n;
    }

    private record QuestionKey(UUID correctOptionId, String categoryCode,
                               String skill, String level) {
    }
}
