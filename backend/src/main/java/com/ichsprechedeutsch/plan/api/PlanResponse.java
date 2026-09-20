package com.ichsprechedeutsch.plan.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        LocalDate startDate,
        LocalDate endDate,
        int totalDays,
        int dailyMinutes,
        /** OK | TIGHT | UNREALISTIC */
        String feasibility,
        int version,
        List<PlanDayResponse> days) {
}
