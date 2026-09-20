package com.ichsprechedeutsch.placement.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record PlacementSubmitRequest(

        @NotEmpty(message = "Cevap gonderilmedi")
        List<Answer> answers) {

    public record Answer(
            @NotNull UUID questionId,
            /** Bos birakilan soru icin null gonderilebilir. */
            UUID selectedOptionId) {
    }
}
