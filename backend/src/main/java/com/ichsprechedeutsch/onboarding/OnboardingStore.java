package com.ichsprechedeutsch.onboarding;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.StartMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Onboarding cevaplari, hedef ve calisma ritmi: kalicilik katmani. */
@Repository
public class OnboardingStore {

    public record Profile(StartMode startMode, OffsetDateTime completedAt) {
    }

    public record Goal(UUID id, Level target, Purpose purpose, OffsetDateTime createdAt) {
    }

    public record Rhythm(int dailyMinutes, int daysPerWeek) {
    }

    private final JdbcTemplate jdbc;

    public OnboardingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Profile> profile(UUID userId) {
        List<Profile> rows = jdbc.query(
                "SELECT start_mode, onboarding_completed_at FROM user_profile WHERE user_id = ?",
                (rs, i) -> new Profile(StartMode.valueOf(rs.getString(1)),
                        rs.getObject(2, OffsetDateTime.class)),
                userId);
        return rows.stream().findFirst();
    }

    public void saveStartMode(UUID userId, StartMode mode) {
        jdbc.update("""
                INSERT INTO user_profile (user_id, start_mode) VALUES (?, ?)
                ON CONFLICT (user_id) DO UPDATE SET start_mode = EXCLUDED.start_mode, updated_at = now()
                """, userId, mode.name());
    }

    public void markCompleted(UUID userId) {
        jdbc.update("""
                UPDATE user_profile SET onboarding_completed_at = coalesce(onboarding_completed_at, now()),
                       updated_at = now()
                WHERE user_id = ?
                """, userId);
    }

    public Optional<Goal> activeGoal(UUID userId) {
        List<Goal> rows = jdbc.query("""
                SELECT id, target_level, purpose, created_at FROM goal
                WHERE user_id = ? AND status = 'ACTIVE'
                """,
                (rs, i) -> new Goal(rs.getObject(1, UUID.class), Level.valueOf(rs.getString(2)),
                        Purpose.valueOf(rs.getString(3)), rs.getObject(4, OffsetDateTime.class)),
                userId);
        return rows.stream().findFirst();
    }

    /** Yeni hedef: eskisi arsivlenir, gecmis korunur. */
    public void replaceGoal(UUID userId, Level target, Purpose purpose) {
        jdbc.update("""
                UPDATE goal SET status = 'ARCHIVED', archived_at = now()
                WHERE user_id = ? AND status = 'ACTIVE'
                """, userId);
        jdbc.update("INSERT INTO goal (user_id, target_level, purpose) VALUES (?, ?, ?)",
                userId, target.name(), purpose.name());
    }

    public Optional<Rhythm> rhythm(UUID userId) {
        List<Rhythm> rows = jdbc.query(
                "SELECT daily_minutes, days_per_week FROM study_rhythm WHERE user_id = ?",
                (rs, i) -> new Rhythm(rs.getInt(1), rs.getInt(2)), userId);
        return rows.stream().findFirst();
    }

    public void saveRhythm(UUID userId, int dailyMinutes, int daysPerWeek) {
        jdbc.update("""
                INSERT INTO study_rhythm (user_id, daily_minutes, days_per_week) VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET daily_minutes = EXCLUDED.daily_minutes,
                    days_per_week = EXCLUDED.days_per_week, updated_at = now()
                """, userId, dailyMinutes, daysPerWeek);
    }
}
