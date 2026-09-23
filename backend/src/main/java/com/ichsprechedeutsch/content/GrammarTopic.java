package com.ichsprechedeutsch.content;

import com.ichsprechedeutsch.common.model.Level;

/**
 * Gramer agaci dugumu. kind=GRAMMAR olmayanlar (VOCABULARY, READING,
 * LISTENING) soru etiketi olarak kullanilan dugumlerdir.
 */
public record GrammarTopic(
        String id,
        String kind,
        Level level,
        String titleTr,
        boolean core,
        int estimatedMinutes,
        boolean verified) {
}
