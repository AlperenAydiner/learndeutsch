package com.ichsprechedeutsch.assessment.api;

import java.util.List;
import java.util.UUID;

public record QuizResultResponse(
        int correct,
        int total,
        List<Review> review,
        String messageTr) {

    /** Her soru icin dogru cevap ve Turkce aciklama. */
    public record Review(
            UUID questionId,
            boolean wasCorrect,
            String correctOptionText,
            String explanationTr) {
    }
}
