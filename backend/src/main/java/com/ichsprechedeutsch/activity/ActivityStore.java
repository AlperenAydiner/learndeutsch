package com.ichsprechedeutsch.activity;

import com.ichsprechedeutsch.activity.ActivityRules.Origin;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.ResultSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Aktivite kaydi ve calisma oturumlari: kalicilik katmani. */
@Repository
public class ActivityStore {

    /** Kayitli bir aktivite (satir). */
    public record Activity(
            UUID id, LocalDate date, ActivityType type, int durationMinutes, Origin origin,
            String sourceKind, Level level, Double score, Double maxScore, ResultSource resultSource,
            String speakingPartner, String speakingTopic, String grammarTopicId, String note,
            String contentId, Boolean contentVerified, UUID studySessionId) {
    }

    private static final String COLUMNS = """
            id, activity_date, type, duration_minutes, origin, source_kind, level, score, max_score,
            result_source, speaking_partner, speaking_topic, grammar_topic_id, note, content_id,
            content_verified, study_session_id
            """;

    private final JdbcTemplate jdbc;

    public ActivityStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(UUID userId, Activity a) {
        return jdbc.queryForObject("INSERT INTO activity_log (user_id, activity_date, type, duration_minutes, "
                        + "origin, source_kind, level, score, max_score, result_source, speaking_partner, "
                        + "speaking_topic, grammar_topic_id, note, content_id, content_verified, study_session_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                UUID.class, userId, a.date(), a.type().name(), a.durationMinutes(), a.origin().name(),
                a.sourceKind(), name(a.level()), a.score(), a.maxScore(), name(a.resultSource()),
                a.speakingPartner(), a.speakingTopic(), a.grammarTopicId(), a.note(), a.contentId(),
                a.contentVerified(), a.studySessionId());
    }

    public boolean update(UUID userId, UUID id, Activity a) {
        return jdbc.update("UPDATE activity_log SET activity_date = ?, type = ?, duration_minutes = ?, "
                        + "source_kind = ?, level = ?, score = ?, max_score = ?, result_source = ?, "
                        + "speaking_partner = ?, speaking_topic = ?, grammar_topic_id = ?, note = ?, "
                        + "updated_at = now() WHERE id = ? AND user_id = ?",
                a.date(), a.type().name(), a.durationMinutes(), a.sourceKind(), name(a.level()), a.score(),
                a.maxScore(), name(a.resultSource()), a.speakingPartner(), a.speakingTopic(),
                a.grammarTopicId(), a.note(), id, userId) == 1;
    }

    public boolean delete(UUID userId, UUID id) {
        return jdbc.update("DELETE FROM activity_log WHERE id = ? AND user_id = ?", id, userId) == 1;
    }

    public Optional<Activity> find(UUID userId, UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM activity_log WHERE id = ? AND user_id = ?",
                this::map, id, userId).stream().findFirst();
    }

    public List<Activity> between(UUID userId, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT " + COLUMNS + " FROM activity_log WHERE user_id = ? "
                        + "AND activity_date BETWEEN ? AND ? ORDER BY activity_date DESC, created_at DESC",
                this::map, userId, from, to);
    }

    // --- calisma oturumu (SPEC 5.2) ---------------------------------

    public UUID startSession(UUID userId, String module) {
        return jdbc.queryForObject(
                "INSERT INTO study_session (user_id, module) VALUES (?, ?) RETURNING id",
                UUID.class, userId, module);
    }

    public void finishSession(UUID userId, UUID sessionId, long activeSeconds) {
        jdbc.update("UPDATE study_session SET finished_at = now(), active_seconds = ? "
                + "WHERE id = ? AND user_id = ?", activeSeconds, sessionId, userId);
    }

    private Activity map(ResultSet rs, int i) throws SQLException {
        String level = rs.getString("level");
        String resultSource = rs.getString("result_source");
        return new Activity(
                rs.getObject("id", UUID.class),
                rs.getObject("activity_date", LocalDate.class),
                ActivityType.valueOf(rs.getString("type")),
                rs.getInt("duration_minutes"),
                Origin.valueOf(rs.getString("origin")),
                rs.getString("source_kind"),
                level == null ? null : Level.valueOf(level),
                number(rs.getBigDecimal("score")),
                number(rs.getBigDecimal("max_score")),
                resultSource == null ? null : ResultSource.valueOf(resultSource),
                rs.getString("speaking_partner"),
                rs.getString("speaking_topic"),
                rs.getString("grammar_topic_id"),
                rs.getString("note"),
                rs.getString("content_id"),
                (Boolean) rs.getObject("content_verified"),
                rs.getObject("study_session_id", UUID.class));
    }

    /** numeric -> Double; PgJDBC dogrudan donusturmuyor. */
    private static Double number(java.math.BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    private static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }
}
