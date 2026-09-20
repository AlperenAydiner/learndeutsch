package com.ichsprechedeutsch.assessment.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record QuizSubmitRequest(

        @NotEmpty(message = "Cevap gonderilmedi")
        List<Answer> answers) {

    public record Answer(
            @NotNull UUID questionId,
            /** Bos birakilan soru icin null. */
            UUID selectedOptionId) {
    }
}
