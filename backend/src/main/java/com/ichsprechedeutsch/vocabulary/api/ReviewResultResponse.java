package com.ichsprechedeutsch.vocabulary.api;

import java.time.LocalDate;

public record ReviewResultResponse(
        int intervalDays,
        LocalDate dueDate,
        double easeFactor,
        boolean correct,
        String nextDueMessageTr) {
}
