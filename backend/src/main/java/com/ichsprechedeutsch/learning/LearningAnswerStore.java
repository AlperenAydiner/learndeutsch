package com.ichsprechedeutsch.learning;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Ogrenme cevaplari (kelime, artikel, ileride gramer). Buradan "son N
 * yanit" pencereleri cikar (SPEC 5.2); seviye kaniti uretmez (K2, 8.5).
 */
@Repository
public class LearningAnswerStore {

    public enum Kind { WORD, ARTICLE, GRAMMAR }

    /** Bir etiketin son N yanittaki durumu. */
    public record Window(int answers, int correct) {
    }

    private final JdbcTemplate jdbc;

    public LearningAnswerStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID userId, UUID sessionId, Kind kind, String itemId, String tag,
                       boolean correct, String given, LocalDate date) {
        jdbc.update("INSERT INTO learning_answer (user_id, session_id, kind, item_id, tag, correct, "
                        + "given, answer_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                userId, sessionId, kind.name(), itemId, tag, correct, given, date);
    }

    /** Etiket bazinda son {@code size} yanit (artikel icin der/die/das). */
    public Map<String, Window> windowsByTag(UUID userId, Kind kind, int size) {
        Map<String, Window> out = new LinkedHashMap<>();
        jdbc.query("""
                        SELECT tag, count(*) AS answers, count(*) FILTER (WHERE correct) AS correct
                        FROM (SELECT tag, correct,
                                     row_number() OVER (PARTITION BY tag ORDER BY created_at DESC) AS rn
                              FROM learning_answer
                              WHERE user_id = ? AND kind = ? AND tag IS NOT NULL) t
                        WHERE rn <= ?
                        GROUP BY tag
                        """,
                rs -> {
                    out.put(rs.getString("tag"), new Window(rs.getInt("answers"), rs.getInt("correct")));
                },
                userId, kind.name(), size);
        return out;
    }

    /** Bir turdeki tum yanitlarin ozeti (Koclukta "ne kadar calistim"). */
    public Window total(UUID userId, Kind kind) {
        return jdbc.query("SELECT count(*) AS answers, count(*) FILTER (WHERE correct) AS correct "
                        + "FROM learning_answer WHERE user_id = ? AND kind = ?",
                (rs, i) -> new Window(rs.getInt("answers"), rs.getInt("correct")),
                userId, kind.name()).getFirst();
    }

    /** Oturum bu kullaniciya mi ait? */
    public boolean ownsSession(UUID userId, UUID sessionId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM study_session WHERE id = ? AND user_id = ?",
                Integer.class, sessionId, userId);
        return n != null && n > 0;
    }

    /** Bir ogedeki (kelime) yanlis sayisi: zayif kelimeleri one almak icin. */
    public Map<String, Window> windowsByItem(UUID userId, Kind kind) {
        Map<String, Window> out = new LinkedHashMap<>();
        jdbc.query("SELECT item_id, count(*) AS answers, count(*) FILTER (WHERE correct) AS correct "
                        + "FROM learning_answer WHERE user_id = ? AND kind = ? GROUP BY item_id",
                rs -> {
                    out.put(rs.getString("item_id"), new Window(rs.getInt("answers"), rs.getInt("correct")));
                },
                userId, kind.name());
        return out;
    }
}
