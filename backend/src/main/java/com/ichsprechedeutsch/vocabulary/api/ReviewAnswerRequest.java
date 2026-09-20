package com.ichsprechedeutsch.vocabulary.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * @param quality 0 bilmiyordum, 3 zor hatirladim, 4 hatirladim, 5 cok kolay
 */
public record ReviewAnswerRequest(

        @Min(value = 0, message = "Gecersiz cevap")
        @Max(value = 5, message = "Gecersiz cevap")
        int quality,

        @NotBlank(message = "Soru yonu gonderilmeli")
        String direction,

        Integer responseMs) {
}
