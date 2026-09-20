package com.ichsprechedeutsch.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Kullanicinin hedefi: nereye, ne kadar surede, gunde kac dakikayla.
 *
 * Program ureticinin tek girdisi budur (yerlestirme sonucuyla birlikte).
 * Kullanici hedefini degistirdiginde eskisi ARCHIVED olur, yenisi ACTIVE
 * acilir; boylece gecmis hedefler ve onlara ait planlar kaybolmaz.
 */
@Entity
@Table(name = "learning_goal")
public class LearningGoal {

    /** Sinavi olmayan kullanici icin. */
    public static final String EXAM_NONE = "NONE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "target_level", nullable = false)
    private String targetLevel;

    @Column(name = "exam_type", nullable = false)
    private String examType;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Column(name = "daily_minutes", nullable = false)
    private int dailyMinutes;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected LearningGoal() {
        // JPA icin
    }

    public LearningGoal(UUID userId, String targetLevel, String examType,
                        int totalDays, int dailyMinutes, LocalDate startDate) {
        this.userId = userId;
        this.targetLevel = targetLevel;
        this.examType = examType;
        this.totalDays = totalDays;
        this.dailyMinutes = dailyMinutes;
        this.startDate = startDate;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public void archive() {
        this.status = "ARCHIVED";
    }

    /** Hedef tarihi: baslangic + sure. */
    public LocalDate targetDate() {
        return startDate.plusDays(totalDays - 1L);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTargetLevel() {
        return targetLevel;
    }

    public String getExamType() {
        return examType;
    }

    public int getTotalDays() {
        return totalDays;
    }

    public int getDailyMinutes() {
        return dailyMinutes;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
