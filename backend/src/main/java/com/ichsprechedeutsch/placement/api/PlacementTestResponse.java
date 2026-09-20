package com.ichsprechedeutsch.placement.api;

import java.util.List;
import java.util.UUID;

/**
 * Yerlestirme testinin kullaniciya gonderilen hali.
 *
 * Secenekler DOGRU CEVAP BILGISI OLMADAN gonderilir; dogruluk yalnizca
 * sunucuda bilinir. Aksi hâlde testin bir olcum degeri kalmazdi.
 */
public record PlacementTestResponse(
        UUID testId,
        String title,
        Integer timeLimitMinutes,
        List<QuestionView> questions) {

    public record QuestionView(
            UUID id,
            String type,
            String prompt,
            List<OptionView> options) {
    }

    public record OptionView(UUID id, String text) {
    }
}
