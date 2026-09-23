package com.ichsprechedeutsch.errors;

import com.ichsprechedeutsch.errors.ErrorMemory.State;
import com.ichsprechedeutsch.errors.ErrorMemory.Status;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Hata hafizasinin etiket bazli durumu (SPEC 8.4). */
@Repository
public class ErrorStore {

    /** Kayitli durum + hangi oturumda son kez hatali geldigi. */
    public record Stored(State state, UUID lastSessionId) {
    }

    private final JdbcTemplate jdbc;

    public ErrorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Stored> find(UUID userId, String tag) {
        return jdbc.query("SELECT * FROM error_record WHERE user_id = ? AND tag = ?",
                ErrorStore::map, userId, tag).stream().findFirst();
    }

    public List<State> all(UUID userId) {
        return jdbc.query("SELECT * FROM error_record WHERE user_id = ? ORDER BY last_seen DESC",
                ErrorStore::map, userId).stream().map(Stored::state).toList();
    }

    public List<State> unresolved(UUID userId) {
        return jdbc.query("SELECT * FROM error_record WHERE user_id = ? AND status <> 'RESOLVED' "
                        + "ORDER BY distinct_sessions DESC, last_seen DESC",
                ErrorStore::map, userId).stream().map(Stored::state).toList();
    }

    public void save(UUID userId, State s, UUID sessionId) {
        jdbc.update("INSERT INTO error_record (user_id, tag, status, occurrences, distinct_sessions, "
                        + "correct_streak, last_session_id, first_seen, last_seen, resolved_on) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (user_id, tag) DO UPDATE SET status = excluded.status, "
                        + "occurrences = excluded.occurrences, distinct_sessions = excluded.distinct_sessions, "
                        + "correct_streak = excluded.correct_streak, last_session_id = excluded.last_session_id, "
                        + "last_seen = excluded.last_seen, resolved_on = excluded.resolved_on, "
                        + "updated_at = now()",
                userId, s.tag(), s.status().name(), s.occurrences(), s.distinctSessions(),
                s.correctStreak(), sessionId, s.firstSeen(), s.lastSeen(), s.resolvedOn());
    }

    private static Stored map(ResultSet rs, int i) throws SQLException {
        State state = new State(rs.getString("tag"), Status.valueOf(rs.getString("status")),
                rs.getInt("occurrences"), rs.getInt("distinct_sessions"), rs.getInt("correct_streak"),
                rs.getObject("first_seen", LocalDate.class), rs.getObject("last_seen", LocalDate.class),
                rs.getObject("resolved_on", LocalDate.class));
        return new Stored(state, rs.getObject("last_session_id", UUID.class));
    }
}
