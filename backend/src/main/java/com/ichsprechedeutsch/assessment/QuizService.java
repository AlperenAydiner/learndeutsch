package com.ichsprechedeutsch.assessment;

import com.ichsprechedeutsch.assessment.api.QuizResultResponse;
import com.ichsprechedeutsch.assessment.api.QuizResponse;
import com.ichsprechedeutsch.assessment.api.QuizSubmitRequest;
import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.common.error.ValidationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gunun gramer quiz'i.
 *
 * Sorular gunun konusunun hata kategorilerinden secilir. Konusu olmayan
 * gunlerde (hafif gun) kullanicinin en zayif kategorilerinden secilir -
 * tekrar gunu bos tekrar degil, hedefli tekrar olsun.
 *
 * Bu quiz, spec'teki "gorev tipine ozel isaretleme"nin gramer ayagidir:
 * kullanici kendi performansini bildirmez, olculur.
 */
@Service
public class QuizService {

    private static final int QUESTION_COUNT = 10;

    private final JdbcTemplate jdbc;

    public QuizService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public QuizResponse todaysQuiz(UUID userId) {
        LocalDate today = LocalDate.now();
        String levels = levelsUpTo(targetLevel(userId));

        List<Map<String, Object>> questions = questionsForToday(userId, today, levels);
        if (questions.isEmpty()) {
            throw new NotFoundException(
                    "Bugun icin quiz hazirlanamadi. Once programini olustur.");
        }

        Map<UUID, List<QuizResponse.Option>> optionsByQuestion = loadOptions(
                questions.stream().map(q -> (UUID) q.get("id")).toList());

        List<QuizResponse.Question> out = new ArrayList<>();
        for (Map<String, Object> q : questions) {
            UUID id = (UUID) q.get("id");
            out.add(new QuizResponse.Question(
                    id,
                    (String) q.get("prompt_de"),
                    (String) q.get("category_name"),
                    optionsByQuestion.getOrDefault(id, List.of())));
        }

        return new QuizResponse((String) questions.get(0).get("topic"), out);
    }

    /**
     * Soru secimi: once gunun konusuna ait olanlar, sonra kullanicinin
     * zayif kategorileri. Daha once hic sorulmamis soru one gelir ki
     * ayni sorular donup durmasin.
     */
    private List<Map<String, Object>> questionsForToday(UUID userId, LocalDate today,
                                                        String levels) {
        return jdbc.queryForList("""
                WITH todays_unit AS (
                    SELECT cu.id, cu.title_tr
                    FROM plan_day d
                    JOIN plan p ON p.id = d.plan_id
                    JOIN content_unit cu ON cu.id = d.content_unit_id
                    WHERE p.user_id = ? AND p.status = 'ACTIVE' AND d.date = ?
                ),
                target_categories AS (
                    -- Gunun konusunun kategorileri
                    SELECT cc.mistake_category_id, 0 AS priority
                    FROM content_unit_category cc
                    JOIN todays_unit u ON u.id = cc.content_unit_id
                    UNION
                    -- Konusu yoksa: en zayif kategoriler
                    SELECT s.mistake_category_id, 1
                    FROM user_category_stat s
                    WHERE s.user_id = ? AND s.attempts > 0
                      AND NOT EXISTS (SELECT 1 FROM todays_unit)
                )
                SELECT q.id, q.prompt_de, mc.name_tr AS category_name,
                       coalesce((SELECT title_tr FROM todays_unit), 'Zayif konular') AS topic,
                       tc.priority,
                       (SELECT count(*) FROM attempt_answer aa
                        JOIN test_attempt ta ON ta.id = aa.attempt_id
                        WHERE ta.user_id = ? AND aa.question_id = q.id) AS seen
                FROM question q
                JOIN target_categories tc ON tc.mistake_category_id = q.mistake_category_id
                JOIN mistake_category mc ON mc.id = q.mistake_category_id
                WHERE q.level = ANY (string_to_array(?, ','))
                ORDER BY tc.priority, seen, q.difficulty, q.code
                LIMIT ?
                """, userId, today, userId, userId, levels, QUESTION_COUNT);
    }

    private Map<UUID, List<QuizResponse.Option>> loadOptions(List<UUID> questionIds) {
        Map<UUID, List<QuizResponse.Option>> out = new LinkedHashMap<>();
        if (questionIds.isEmpty()) {
            return out;
        }

        String ids = questionIds.stream().map(UUID::toString)
                .reduce((a, b) -> a + "," + b).orElse("");

        jdbc.queryForList("""
                SELECT o.id, o.question_id, o.option_text
                FROM question_option o
                WHERE o.question_id = ANY (string_to_array(?, ',')::uuid[])
                ORDER BY o.question_id, o.order_no
                """, ids).forEach(row -> out
                .computeIfAbsent((UUID) row.get("question_id"), k -> new ArrayList<>())
                .add(new QuizResponse.Option(
                        (UUID) row.get("id"), (String) row.get("option_text"))));

        return out;
    }

    // ------------------------------------------------------------------

    @Transactional
    public QuizResultResponse submit(UUID userId, QuizSubmitRequest request) {
        if (request.answers() == null || request.answers().isEmpty()) {
            throw new ValidationException("Cevap gonderilmedi");
        }

        LocalDate today = LocalDate.now();
        UUID testId = ensureQuizTest();

        UUID attemptId = jdbc.queryForObject("""
                INSERT INTO test_attempt (user_id, test_id, status)
                VALUES (?, ?, 'IN_PROGRESS') RETURNING id
                """, UUID.class, userId, testId);

        Map<UUID, AnswerKey> keys = loadKeys(
                request.answers().stream().map(QuizSubmitRequest.Answer::questionId).toList());

        List<Object[]> rows = new ArrayList<>();
        Map<String, int[]> perCategory = new LinkedHashMap<>();
        List<QuizResultResponse.Review> review = new ArrayList<>();
        int correct = 0;

        for (QuizSubmitRequest.Answer answer : request.answers()) {
            AnswerKey key = keys.get(answer.questionId());
            if (key == null) {
                throw new ValidationException("Bilinmeyen soru gonderildi");
            }

            boolean ok = answer.selectedOptionId() != null
                    && answer.selectedOptionId().equals(key.correctOptionId());
            if (ok) {
                correct++;
            }

            rows.add(new Object[]{attemptId, answer.questionId(),
                    answer.selectedOptionId(), ok});

            int[] c = perCategory.computeIfAbsent(key.categoryCode(), k -> new int[2]);
            if (ok) {
                c[0]++;
            }
            c[1]++;

            review.add(new QuizResultResponse.Review(
                    answer.questionId(), ok, key.correctOptionText(), key.explanationTr()));
        }

        jdbc.batchUpdate("""
                INSERT INTO attempt_answer
                    (attempt_id, question_id, selected_option_id, is_correct)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (attempt_id, question_id) DO UPDATE SET
                    selected_option_id = EXCLUDED.selected_option_id,
                    is_correct = EXCLUDED.is_correct
                """, rows);

        int total = request.answers().size();
        jdbc.update("""
                UPDATE test_attempt
                SET status = 'COMPLETED', finished_at = now(), score = ?, max_score = ?
                WHERE id = ?
                """, (double) correct, (double) total, attemptId);

        updateCategoryStats(userId, perCategory);
        markGrammarTask(userId, today, correct, total);

        return new QuizResultResponse(correct, total, review,
                mesaj(correct, total));
    }

    /** Quiz denemelerinin baglandigi tek bir test kaydi. */
    private UUID ensureQuizTest() {
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM test WHERE code = 'DAILY_QUIZ'", UUID.class);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return jdbc.queryForObject("""
                INSERT INTO test (code, test_type, level, title_tr, question_count)
                VALUES ('DAILY_QUIZ', 'CHECKPOINT', 'A2', 'Gunluk gramer quiz', ?)
                RETURNING id
                """, UUID.class, QUESTION_COUNT);
    }

    private record AnswerKey(UUID correctOptionId, String correctOptionText,
                             String categoryCode, String explanationTr) {
    }

    private Map<UUID, AnswerKey> loadKeys(List<UUID> questionIds) {
        String ids = questionIds.stream().map(UUID::toString)
                .reduce((a, b) -> a + "," + b).orElse("");

        Map<UUID, AnswerKey> keys = new LinkedHashMap<>();
        jdbc.queryForList("""
                SELECT q.id, mc.code AS category_code, q.explanation_tr,
                       o.id AS option_id, o.option_text
                FROM question q
                JOIN mistake_category mc ON mc.id = q.mistake_category_id
                JOIN question_option o ON o.question_id = q.id AND o.is_correct
                WHERE q.id = ANY (string_to_array(?, ',')::uuid[])
                """, ids).forEach(row -> keys.put(
                (UUID) row.get("id"),
                new AnswerKey(
                        (UUID) row.get("option_id"),
                        (String) row.get("option_text"),
                        (String) row.get("category_code"),
                        (String) row.get("explanation_tr"))));
        return keys;
    }

    private void updateCategoryStats(UUID userId, Map<String, int[]> perCategory) {
        List<Object[]> batch = new ArrayList<>();
        perCategory.forEach((code, c) -> batch.add(new Object[]{userId, c[0], c[1], code}));

        jdbc.batchUpdate("""
                INSERT INTO user_category_stat
                    (user_id, mistake_category_id, correct, attempts, weight, updated_at)
                SELECT ?, mc.id, ?, ?, 1.0, now()
                FROM mistake_category mc
                WHERE mc.code = ?
                ON CONFLICT (user_id, mistake_category_id) DO UPDATE SET
                    correct = user_category_stat.correct + EXCLUDED.correct,
                    attempts = user_category_stat.attempts + EXCLUDED.attempts,
                    weight = round(1.0 + (1.0 - (user_category_stat.correct
                             + EXCLUDED.correct)::numeric
                             / (user_category_stat.attempts + EXCLUDED.attempts)) * 1.5, 2),
                    updated_at = now()
                """, batch);
    }

    /**
     * Quiz cozuldugunde gunun GRAMER gorevi kendiliginden isaretlenir:
     * kullanici ayrica "yaptim" demek zorunda kalmasin, skor zaten elimizde.
     */
    private void markGrammarTask(UUID userId, LocalDate today, int correct, int total) {
        double score = total == 0 ? 0 : (double) correct / total * 100;
        String status = score >= 60 ? "DONE" : "PARTIAL";

        jdbc.update("""
                INSERT INTO task_completion
                    (task_id, user_id, status, self_report, auto_score, completed_at)
                SELECT t.id, ?, ?, '{}'::jsonb, ?, now()
                FROM task t
                JOIN plan_day d ON d.id = t.plan_day_id
                JOIN plan p ON p.id = d.plan_id
                WHERE p.user_id = ? AND p.status = 'ACTIVE'
                  AND d.date = ? AND t.task_type = 'GRAMER'
                ON CONFLICT (task_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    auto_score = EXCLUDED.auto_score,
                    completed_at = now()
                """, userId, status, score, userId, today);
    }

    private String mesaj(int correct, int total) {
        double oran = total == 0 ? 0 : (double) correct / total;
        if (oran >= 0.9) {
            return "Bu konu oturmus gorunuyor.";
        }
        if (oran >= 0.6) {
            return "Fena degil. Yanlislarini gozden gecir, yarin tekrar karsina cikacak.";
        }
        return "Bu konu henuz oturmamis. Programda ona daha fazla yer acacagiz.";
    }

    // ------------------------------------------------------------------

    private String targetLevel(UUID userId) {
        List<String> levels = jdbc.queryForList("""
                SELECT target_level FROM learning_goal
                WHERE user_id = ? AND status = 'ACTIVE'
                """, String.class, userId);
        return levels.isEmpty() ? "A1" : levels.get(0);
    }

    private String levelsUpTo(String targetLevel) {
        return switch (targetLevel) {
            case "A1" -> "A1";
            case "A2" -> "A1,A2";
            default -> "A1,A2,B1";
        };
    }
}
