package com.ichsprechedeutsch.plan.api;

import java.util.UUID;

public record TaskResponse(
        UUID id,
        String taskType,
        int plannedMinutes,
        int orderNo,
        String instructionTr,
        /** Isaretlenmediyse null. */
        String status) {
}
