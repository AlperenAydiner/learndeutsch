package com.ichsprechedeutsch.grammar;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Gramer konusundaki kullanici durumu. Icerik katalogda (K-004). */
@Repository
public class GrammarStore {

    /**
     * @param completedOn konu dongusu tamamlandigi tarih; null ise devam ediyor
     * @param checkIndex  kacinci kontrol tekrari yapildi
     */
    public record Progress(String topicId, int answers, int correct, LocalDate lastStudied,
                           LocalDate completedOn, int checkIndex, LocalDate nextCheck) {

        public double ratio() {
            return answers == 0 ? 0 : (double) correct / answers;
        }
    }

    private final JdbcTemplate jdbc;

    public GrammarStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Progress> all(UUID userId) {
        return jdbc.query("SELECT * FROM grammar_progress WHERE user_id = ?", GrammarStore::map, userId);
    }

    public Optional<Progress> find(UUID userId, String topicId) {
        return jdbc.query("SELECT * FROM grammar_progress WHERE user_id = ? AND topic_id = ?",
                GrammarStore::map, userId, topicId).stream().findFirst();
    }

    /** Bir cevabi konunun sayaclarina yazar. */
    public void recordAnswer(UUID userId, String topicId, boolean correct, LocalDate today) {
        jdbc.update("INSERT INTO grammar_progress (user_id, topic_id, answers, correct, last_studied) "
                        + "VALUES (?, ?, 1, ?, ?) "
                        + "ON CONFLICT (user_id, topic_id) DO UPDATE SET "
                        + "answers = grammar_progress.answers + 1, "
                        + "correct = grammar_progress.correct + ?, "
                        + "last_studied = excluded.last_studied, updated_at = now()",
                userId, topicId, correct ? 1 : 0, today, correct ? 1 : 0);
    }

    /** Konu dongusu tamamlandi: kontrol tekrarlari bastan planlanir. */
    public void complete(UUID userId, String topicId, LocalDate today, LocalDate nextCheck) {
        jdbc.update("INSERT INTO grammar_progress (user_id, topic_id, last_studied, completed_on, "
                        + "check_index, next_check) VALUES (?, ?, ?, ?, 0, ?) "
                        + "ON CONFLICT (user_id, topic_id) DO UPDATE SET completed_on = excluded.completed_on, "
                        + "check_index = 0, next_check = excluded.next_check, "
                        + "last_studied = excluded.last_studied, updated_at = now()",
                userId, topicId, today, today, nextCheck);
    }

    /** Kontrol tekrari yapildi: sonraki kontrol (ya da null = sira bitti). */
    public void advanceCheck(UUID userId, String topicId, int checkIndex, LocalDate nextCheck) {
        jdbc.update("UPDATE grammar_progress SET check_index = ?, next_check = ?, updated_at = now() "
                + "WHERE user_id = ? AND topic_id = ?", checkIndex, nextCheck, userId, topicId);
    }

    public List<String> dueCheckTopics(UUID userId, LocalDate today) {
        return jdbc.queryForList("SELECT topic_id FROM grammar_progress WHERE user_id = ? "
                + "AND next_check IS NOT NULL AND next_check <= ? ORDER BY next_check", String.class,
                userId, today);
    }

    private static Progress map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Progress(rs.getString("topic_id"), rs.getInt("answers"), rs.getInt("correct"),
                rs.getObject("last_studied", LocalDate.class), rs.getObject("completed_on", LocalDate.class),
                rs.getInt("check_index"), rs.getObject("next_check", LocalDate.class));
    }
}
