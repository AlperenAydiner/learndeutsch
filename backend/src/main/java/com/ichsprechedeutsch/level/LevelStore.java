package com.ichsprechedeutsch.level;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.ResultSource;
import com.ichsprechedeutsch.common.model.Skill;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Beceri kanitlari ve seviye degerlendirmeleri: kalicilik katmani. */
@Repository
public class LevelStore {

    /** Yerlestirme veya oz degerlendirme kaydi. result A0 = "A1'in altinda". */
    public record Assessment(UUID id, String kind, Level result, String confidence,
                             Double score, Double maxScore, LocalDate takenOn) {
    }

    private final JdbcTemplate jdbc;

    public LevelStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Evidence> evidence(UUID userId) {
        return jdbc.query("""
                SELECT id, skill, level, source, score, max_score, evidence_date, content_verified
                FROM skill_evidence WHERE user_id = ?
                """,
                (rs, i) -> new Evidence(
                        rs.getObject(1, UUID.class),
                        Skill.valueOf(rs.getString(2)),
                        Level.valueOf(rs.getString(3)),
                        ResultSource.valueOf(rs.getString(4)),
                        rs.getDouble(5), rs.getDouble(6),
                        rs.getObject(7, LocalDate.class),
                        (Boolean) rs.getObject(8)),
                userId);
    }

    public void insertEvidence(UUID userId, UUID activityId, Skill skill, Level level, ResultSource source,
                               double score, double maxScore, LocalDate date, String contentId,
                               Boolean contentVerified) {
        jdbc.update("""
                INSERT INTO skill_evidence (user_id, activity_id, skill, level, source, score, max_score,
                                            evidence_date, content_id, content_verified)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, activityId, skill.name(), level.name(), source.name(), score, maxScore,
                date, contentId, contentVerified);
    }

    public void deleteEvidenceOfActivity(UUID userId, UUID activityId) {
        jdbc.update("DELETE FROM skill_evidence WHERE user_id = ? AND activity_id = ?", userId, activityId);
    }

    public Optional<Assessment> latestAssessment(UUID userId, String kind) {
        List<Assessment> rows = jdbc.query("""
                SELECT id, kind, result, confidence, score, max_score, taken_on
                FROM level_assessment WHERE user_id = ? AND (?::text IS NULL OR kind = ?::text)
                ORDER BY taken_on DESC, created_at DESC LIMIT 1
                """,
                (rs, i) -> new Assessment(rs.getObject(1, UUID.class), rs.getString(2),
                        Level.valueOf(rs.getString(3)), rs.getString(4),
                        number(rs.getBigDecimal(5)), number(rs.getBigDecimal(6)),
                        rs.getObject(7, LocalDate.class)),
                userId, kind, kind);
        return rows.stream().findFirst();
    }

    public void insertAssessment(UUID userId, String kind, Level result, double score, double maxScore,
                                 String detailsJson, LocalDate takenOn, UUID sourceId) {
        jdbc.update("""
                INSERT INTO level_assessment (user_id, kind, result, confidence, score, max_score, details,
                                              taken_on, source_id)
                VALUES (?, ?, ?, 'LOW', ?, ?, ?::jsonb, ?, ?)
                """, userId, kind, result.name(), score, maxScore, detailsJson, takenOn, sourceId);
    }

    public int evidenceCountSince(UUID userId, LocalDate since) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM skill_evidence WHERE user_id = ? AND created_at::date > ?",
                Integer.class, userId, since);
        return n == null ? 0 : n;
    }

    /** numeric -> Double; PgJDBC dogrudan donusturmuyor. */
    static Double number(java.math.BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}
