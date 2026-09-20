package com.ichsprechedeutsch.plan.generator;

import java.util.UUID;

public record PlannedTask(
        String taskType,
        int plannedMinutes,
        int orderNo,
        String instructionTr,
        UUID contentUnitId) {
}
