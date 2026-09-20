package com.ichsprechedeutsch.plan.generator;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlannedDay(
        int dayNumber,
        LocalDate date,
        /** NORMAL | LIGHT | TEST | MOCK_EXAM */
        String dayType,
        UUID contentUnitId,
        String unitCode,
        String titleTr,
        int plannedMinutes,
        List<PlannedTask> tasks) {
}
