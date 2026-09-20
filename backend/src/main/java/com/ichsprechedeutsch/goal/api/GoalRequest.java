package com.ichsprechedeutsch.goal.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * Hedef formu. Tum alanlar onceden tanimli seceneklerden gelir;
 * kullanici serbest metin yazmaz.
 */
public record GoalRequest(

        @NotBlank(message = "Hedef seviye secilmeli")
        String targetLevel,

        @NotBlank(message = "Sinav secimi yapilmali")
        String examType,

        @Min(value = 7, message = "En az 7 gun secmelisin")
        @Max(value = 365, message = "En fazla 365 gun secebilirsin")
        int totalDays,

        @Min(value = 15, message = "Gunde en az 15 dakika ayirmalisin")
        @Max(value = 720, message = "Gunde en fazla 12 saat secebilirsin")
        int dailyMinutes,

        /** Bos birakilirsa bugun kabul edilir. */
        LocalDate startDate) {
}
