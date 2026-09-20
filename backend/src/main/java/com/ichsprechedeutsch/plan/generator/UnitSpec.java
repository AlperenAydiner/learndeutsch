package com.ichsprechedeutsch.plan.generator;

import java.util.List;
import java.util.UUID;

/**
 * Program ureticinin gordugu haliyle bir icerik birimi.
 *
 * Veritabani satiri degil, ureticinin ihtiyaci kadarini tasiyan duz bir
 * kayit. Uretici boylece veritabanindan bagimsiz kalir ve test edilebilir.
 */
public record UnitSpec(
        UUID id,
        String code,
        String level,
        String phase,
        int sequenceNo,
        String titleTr,
        String grammarSummary,
        int estimatedMinutes,
        boolean isNewContent,
        List<String> categoryCodes) {
}
