package com.ichsprechedeutsch.placement;

import com.ichsprechedeutsch.common.model.Level;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** Yerlestirme oturumlari: kalicilik katmani. Durum JSON olarak saklanir. */
@Repository
public class PlacementStore {

    /** Bir blok: sorulan sorular ve (cevaplandiysa) sonucu. */
    public record BlockState(Level level, List<String> questionIds, Map<String, String> answers,
                             Integer correct, long activeSeconds) {
        public boolean answered() {
            return correct != null;
        }
    }

    public record State(List<BlockState> blocks) {
    }

    public record Session(UUID id, UUID studySessionId, String status, State state) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PlacementStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public UUID create(UUID userId, UUID studySessionId, State state) {
        return jdbc.queryForObject("""
                INSERT INTO placement_session (user_id, study_session_id, state)
                VALUES (?, ?, ?::jsonb) RETURNING id
                """, UUID.class, userId, studySessionId, mapper.writeValueAsString(state));
    }

    public Optional<Session> find(UUID userId, UUID id) {
        return jdbc.query("""
                SELECT id, study_session_id, status, state::text FROM placement_session
                WHERE id = ? AND user_id = ?
                """,
                (rs, i) -> new Session(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), mapper.readValue(rs.getString(4), State.class)),
                id, userId).stream().findFirst();
    }

    public void saveState(UUID id, State state) {
        jdbc.update("UPDATE placement_session SET state = ?::jsonb WHERE id = ?",
                mapper.writeValueAsString(state), id);
    }

    public void complete(UUID id, State state, Level result) {
        jdbc.update("""
                UPDATE placement_session SET state = ?::jsonb, status = 'COMPLETED',
                       result_level = ?, finished_at = now()
                WHERE id = ?
                """, mapper.writeValueAsString(state), result.name(), id);
    }

    public void abandonOpen(UUID userId) {
        jdbc.update("""
                UPDATE placement_session SET status = 'ABANDONED', finished_at = now()
                WHERE user_id = ? AND status = 'IN_PROGRESS'
                """, userId);
    }

    /** Kullanicinin daha once gordugu tum yerlestirme sorulari (tekrar cozumde tekrar gelmesin). */
    public Set<String> seenQuestionIds(UUID userId) {
        Set<String> out = new HashSet<>();
        jdbc.query("""
                SELECT jsonb_array_elements_text(b -> 'questionIds')
                FROM placement_session, jsonb_array_elements(state -> 'blocks') b
                WHERE user_id = ?
                """, rs -> {
            out.add(rs.getString(1));
        }, userId);
        return out;
    }
}
