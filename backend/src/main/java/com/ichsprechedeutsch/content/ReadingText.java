package com.ichsprechedeutsch.content;

import com.ichsprechedeutsch.common.model.Level;
import java.util.List;

/**
 * Lesen metni ve sorulari (SPEC 7). Site ici okuma testi otomatik
 * puanlanir ve Lesen kaniti uretir (4.2).
 */
public record ReadingText(
        String id,
        Level level,
        String titleTr,
        String topicTr,
        String text,
        int estimatedMinutes,
        List<Item> questions,
        /** Metinde gecen zor kelimeler; okurken yardimci olur. */
        List<Gloss> glossary,
        boolean verified) {

    public record Item(String id, String type, String prompt, List<String> options, String answer,
                       List<String> acceptedAnswers, String explanationTr) {
    }

    public record Gloss(String de, String tr) {
    }
}
