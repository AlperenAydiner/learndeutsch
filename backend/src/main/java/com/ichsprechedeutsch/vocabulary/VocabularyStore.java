package com.ichsprechedeutsch.vocabulary;

import com.ichsprechedeutsch.srs.Grade;
import com.ichsprechedeutsch.vocabulary.VocabularyPlanner.DueCard;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Kelime SRS durumu. Icerik burada tutulmaz; yalniz kelime id'si (K-004). */
@Repository
public class VocabularyStore {

    /** Bir kullanicinin bir kelimedeki durumu. */
    public record Progress(String wordId, int step, LocalDate due, String lastGrade, int reviews,
                           int lapses) {
    }

    private final JdbcTemplate jdbc;

    public VocabularyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DueCard> cards(UUID userId) {
        return jdbc.query("SELECT word_id, step, due_date FROM vocabulary_progress WHERE user_id = ?",
                (rs, i) -> new DueCard(rs.getString("word_id"), rs.getInt("step"),
                        rs.getObject("due_date", LocalDate.class)),
                userId);
    }

    public Optional<Progress> find(UUID userId, String wordId) {
        return jdbc.query("SELECT * FROM vocabulary_progress WHERE user_id = ? AND word_id = ?",
                (rs, i) -> new Progress(rs.getString("word_id"), rs.getInt("step"),
                        rs.getObject("due_date", LocalDate.class), rs.getString("last_grade"),
                        rs.getInt("reviews"), rs.getInt("lapses")),
                userId, wordId).stream().findFirst();
    }

    public Set<String> seenWordIds(UUID userId) {
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT word_id FROM vocabulary_progress WHERE user_id = ?", String.class, userId));
    }

    public int dueCount(UUID userId, LocalDate today) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM vocabulary_progress WHERE user_id = ? AND due_date <= ?",
                Integer.class, userId, today);
        return n == null ? 0 : n;
    }

    /** Bugun vadesi gelen kelimelerin id'leri (sure tahmini icin). */
    public List<String> dueWordIds(UUID userId, LocalDate today) {
        return jdbc.queryForList("SELECT word_id FROM vocabulary_progress WHERE user_id = ? "
                + "AND due_date <= ? ORDER BY due_date", String.class, userId, today);
    }

    /** Kac kelime calisildi ve kaci verilen basamagin ustunde ("guclu"). */
    public int countAtLeastStep(UUID userId, int step) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM vocabulary_progress WHERE user_id = ? AND step >= ?",
                Integer.class, userId, step);
        return n == null ? 0 : n;
    }

    public int countStudied(UUID userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM vocabulary_progress WHERE user_id = ?",
                Integer.class, userId);
        return n == null ? 0 : n;
    }

    /** Ilk kez calisilan kelimeyi merdivene sokar. */
    public void insertNew(UUID userId, String wordId, int step, LocalDate due, Grade grade, LocalDate today) {
        jdbc.update("INSERT INTO vocabulary_progress (user_id, word_id, step, due_date, last_grade, "
                        + "reviews, lapses, first_seen, last_seen) VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?) "
                        + "ON CONFLICT (user_id, word_id) DO UPDATE SET step = excluded.step, "
                        + "due_date = excluded.due_date, last_grade = excluded.last_grade, "
                        + "reviews = vocabulary_progress.reviews + 1, last_seen = excluded.last_seen, "
                        + "updated_at = now()",
                userId, wordId, step, due, grade.name(), grade == Grade.BILEMEDIM ? 1 : 0, today, today);
    }

    public void update(UUID userId, String wordId, int step, LocalDate due, Grade grade, LocalDate today) {
        jdbc.update("UPDATE vocabulary_progress SET step = ?, due_date = ?, last_grade = ?, "
                        + "reviews = reviews + 1, lapses = lapses + ?, last_seen = ?, updated_at = now() "
                        + "WHERE user_id = ? AND word_id = ?",
                step, due, grade.name(), grade == Grade.BILEMEDIM ? 1 : 0, today, userId, wordId);
    }
}
