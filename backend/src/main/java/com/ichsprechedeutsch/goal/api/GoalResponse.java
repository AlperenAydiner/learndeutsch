package com.ichsprechedeutsch.goal.api;

import com.ichsprechedeutsch.goal.LearningGoal;
import java.time.LocalDate;
import java.util.UUID;

public record GoalResponse(
        UUID id,
        String targetLevel,
        String examType,
        int totalDays,
        int dailyMinutes,
        LocalDate startDate,
        LocalDate targetDate,
        String status) {

    public static GoalResponse from(LearningGoal goal) {
        return new GoalResponse(
                goal.getId(), goal.getTargetLevel(), goal.getExamType(),
                goal.getTotalDays(), goal.getDailyMinutes(), goal.getStartDate(),
                goal.targetDate(), goal.getStatus());
    }
}
