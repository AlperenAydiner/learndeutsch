package com.ichsprechedeutsch.vocabulary;

import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.vocabulary.api.ReviewAnswerRequest;
import com.ichsprechedeutsch.vocabulary.api.ReviewResultResponse;
import com.ichsprechedeutsch.vocabulary.api.SessionResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kelime tekrar oturumu.
 *
 * Kelimeleri kullanici eklemez, sistem verir: gunun konusunun temasindan
 * ve seviyesinden secilir. Gunluk yeni kelime sayisi bekleyen tekrar
 * yukune gore otomatik kisilir.
 */
@Service
public class VocabularyService {

    /** Kelime blogu bulunamazsa varsayilan sure. */
    private static final int DEFAULT_BLOCK_MINUTES = 30;

    private final JdbcTemplate jdbc;
    private final Sm2Scheduler scheduler = new Sm2Scheduler();
    private final VocabularyQuota quotaCalculator = new VocabularyQuota();

    public VocabularyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    // Oturum
    // ------------------------------------------------------------------

    @Transactional
    public SessionResponse session(UUID userId) {
        String targetLevel = targetLevel(userId);
        LocalDate today = LocalDate.now();

        int blockMinutes = todaysVocabBlockMinutes(userId, today);
        int dueCount = countDue(userId, today);
        int available = countAvailable(userId, targetLevel);

        VocabularyQuota.Quota quota =
                quotaCalculator.calculate(blockMinutes, dueCount, available);

        if (quota.newWords() > 0) {
            introduceNewWords(userId, targetLevel, quota.newWords(), today);
        }

        List<SessionResponse.Card> cards = loadCards(userId, today);

        return new SessionResponse(
                cards, quota.newWords(), dueCount, blockMinutes,
                quota.reasonTr(), learnedTotal(userId));
    }

    /** Gunun kelime blogunun suresi; plan yoksa makul bir varsayilan. */
    private int todaysVocabBlockMinutes(UUID userId, LocalDate date) {
        List<Integer> minutes = jdbc.queryForList("""
                SELECT t.planned_minutes
                FROM task t
                JOIN plan_day d ON d.id = t.plan_day_id
                JOIN plan p ON p.id = d.plan_id
                WHERE p.user_id = ? AND p.status = 'ACTIVE'
                  AND d.date = ? AND t.task_type = 'KELIME'
                """, Integer.class, userId, date);

        return minutes.isEmpty() ? DEFAULT_BLOCK_MINUTES : minutes.get(0);
    }

    private int countDue(UUID userId, LocalDate date) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM user_word
                WHERE user_id = ? AND state <> 'SUSPENDED'
                  AND due_date IS NOT NULL AND due_date <= ?
                """, Integer.class, userId, date);
        return n == null ? 0 : n;
    }

    private int countAvailable(UUID userId, String targetLevel) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM word w
                WHERE w.level = ANY (string_to_array(?, ','))
                  AND NOT EXISTS (
                      SELECT 1 FROM user_word uw
                      WHERE uw.user_id = ? AND uw.word_id = w.id
                  )
                """, Integer.class, levelsUpTo(targetLevel), userId);
        return n == null ? 0 : n;
    }

    /**
     * Yeni kelimeleri kullaniciya acar.
     *
     * Sira onemli: once bugunun konusunun temasindaki kelimeler gelir.
     * Gunun gramer konusuyla kelime temasinin ortusmesi, ikisinin de
     * daha kolay oturmasini saglar.
     */
    private void introduceNewWords(UUID userId, String targetLevel, int count,
                                   LocalDate today) {
        jdbc.update("""
                INSERT INTO user_word (user_id, word_id, due_date, state)
                SELECT ?, w.id, ?, 'NEW'
                FROM word w
                LEFT JOIN (
                    SELECT cu.vocab_theme
                    FROM plan_day d
                    JOIN plan p ON p.id = d.plan_id
                    JOIN content_unit cu ON cu.id = d.content_unit_id
                    WHERE p.user_id = ? AND p.status = 'ACTIVE' AND d.date = ?
                ) today_theme ON true
                WHERE w.level = ANY (string_to_array(?, ','))
                  AND NOT EXISTS (
                      SELECT 1 FROM user_word uw
                      WHERE uw.user_id = ? AND uw.word_id = w.id
                  )
                ORDER BY (w.theme IS DISTINCT FROM today_theme.vocab_theme), w.level, w.lemma
                LIMIT ?
                ON CONFLICT (user_id, word_id) DO NOTHING
                """,
                userId, today, userId, today, levelsUpTo(targetLevel), userId, count);
    }

    private List<SessionResponse.Card> loadCards(UUID userId, LocalDate today) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT uw.id AS user_word_id, uw.repetition, uw.state,
                       w.lemma, w.word_type, w.article, w.plural_form, w.meaning_tr,
                       w.example_de, w.example_tr
                FROM user_word uw
                JOIN word w ON w.id = uw.word_id
                WHERE uw.user_id = ? AND uw.state <> 'SUSPENDED'
                  AND (uw.due_date IS NULL OR uw.due_date <= ?)
                ORDER BY (uw.state = 'NEW'), uw.due_date NULLS FIRST, w.lemma
                """, userId, today);

        List<SessionResponse.Card> cards = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            boolean isNoun = "NOUN".equals(row.get("word_type"));
            int repetition = (Integer) row.get("repetition");
            String direction = ReviewDirection.pick(repetition, isNoun);

            cards.add(new SessionResponse.Card(
                    (UUID) row.get("user_word_id"),
                    direction,
                    (String) row.get("lemma"),
                    (String) row.get("article"),
                    (String) row.get("plural_form"),
                    (String) row.get("meaning_tr"),
                    (String) row.get("example_de"),
                    (String) row.get("example_tr"),
                    "NEW".equals(row.get("state"))));
        }
        return cards;
    }

    private int learnedTotal(UUID userId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM user_word
                WHERE user_id = ? AND repetition > 0
                """, Integer.class, userId);
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------
    // Cevap
    // ------------------------------------------------------------------

    @Transactional
    public ReviewResultResponse review(UUID userId, UUID userWordId,
                                       ReviewAnswerRequest request) {
        if (request.quality() < 0 || request.quality() > 5) {
            throw new ValidationException("Cevap kalitesi 0-5 arasinda olmali");
        }

        Map<String, Object> row = findOwned(userId, userWordId);

        Sm2Scheduler.State current = new Sm2Scheduler.State(
                ((Number) row.get("ease_factor")).doubleValue(),
                (Integer) row.get("interval_days"),
                (Integer) row.get("repetition"),
                (Integer) row.get("lapses"),
                (String) row.get("state"));

        Sm2Scheduler.State next = scheduler.review(current, request.quality());
        LocalDate due = LocalDate.now().plusDays(next.intervalDays());

        jdbc.update("""
                UPDATE user_word
                SET ease_factor = ?, interval_days = ?, repetition = ?, lapses = ?,
                    state = ?, due_date = ?, last_reviewed_at = now()
                WHERE id = ?
                """,
                next.easeFactor(), next.intervalDays(), next.repetition(),
                next.lapses(), next.state(), due, userWordId);

        boolean correct = request.quality() >= Sm2Scheduler.PASS_THRESHOLD;

        jdbc.update("""
                INSERT INTO word_review_log
                    (user_word_id, direction, quality, correct, response_ms)
                VALUES (?, ?, ?, ?, ?)
                """, userWordId, request.direction(), request.quality(), correct,
                request.responseMs());

        updateVocabStat(userId, correct);

        return new ReviewResultResponse(
                next.intervalDays(), due, next.easeFactor(), correct,
                nextDueMessage(next.intervalDays()));
    }

    private Map<String, Object> findOwned(UUID userId, UUID userWordId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ease_factor, interval_days, repetition, lapses, state
                FROM user_word WHERE id = ? AND user_id = ?
                """, userWordId, userId);

        if (rows.isEmpty()) {
            throw new NotFoundException("Kelime bulunamadi");
        }
        return rows.get(0);
    }

    /** Kelime performansi da adaptif motora akar. */
    private void updateVocabStat(UUID userId, boolean correct) {
        jdbc.update("""
                INSERT INTO user_category_stat
                    (user_id, mistake_category_id, correct, attempts, weight, updated_at)
                SELECT ?, mc.id, ?, 1, 1.0, now()
                FROM mistake_category mc
                WHERE mc.code = 'WORTSCHATZ'
                ON CONFLICT (user_id, mistake_category_id) DO UPDATE SET
                    correct = user_category_stat.correct + EXCLUDED.correct,
                    attempts = user_category_stat.attempts + 1,
                    last_wrong_at = CASE WHEN EXCLUDED.correct = 0
                                         THEN now() ELSE user_category_stat.last_wrong_at END,
                    updated_at = now()
                """, userId, correct ? 1 : 0);
    }

    private String nextDueMessage(int intervalDays) {
        if (intervalDays <= 1) {
            return "Yarin tekrar karsina cikacak.";
        }
        if (intervalDays < 30) {
            return intervalDays + " gun sonra tekrar sorulacak.";
        }
        return Math.round(intervalDays / 30.0) + " ay sonra tekrar sorulacak.";
    }

    // ------------------------------------------------------------------

    private String targetLevel(UUID userId) {
        List<String> levels = jdbc.queryForList("""
                SELECT target_level FROM learning_goal
                WHERE user_id = ? AND status = 'ACTIVE'
                """, String.class, userId);

        return levels.isEmpty() ? "A1" : levels.get(0);
    }

    /** Hedef A2 ise A1 kelimeleri de havuza dahildir. */
    private String levelsUpTo(String targetLevel) {
        return switch (targetLevel) {
            case "A1" -> "A1";
            case "A2" -> "A1,A2";
            default -> "A1,A2,B1";
        };
    }
}
