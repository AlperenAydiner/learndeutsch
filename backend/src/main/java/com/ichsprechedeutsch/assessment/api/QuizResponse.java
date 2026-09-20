package com.ichsprechedeutsch.assessment.api;

import java.util.List;
import java.util.UUID;

/** Gunun quiz'i. Dogru cevap bilgisi gonderilmez. */
public record QuizResponse(String topicTr, List<Question> questions) {

    public record Question(
            UUID id,
            String promptDe,
            String categoryNameTr,
            List<Option> options) {
    }

    public record Option(UUID id, String text) {
    }
}
