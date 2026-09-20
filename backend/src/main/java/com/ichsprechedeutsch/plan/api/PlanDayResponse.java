package com.ichsprechedeutsch.plan.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanDayResponse(
        UUID id,
        int dayNumber,
        LocalDate date,
        String dayType,
        String unitCode,
        String titleTr,
        String grammarSummary,
        String vocabTheme,
        int plannedMinutes,
        String status,
        List<TaskResponse> tasks) {
}
