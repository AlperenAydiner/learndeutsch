package com.ichsprechedeutsch.content;

import com.ichsprechedeutsch.common.model.Level;

public record Word(
        String id,
        String lemma,
        /** der / die / das; isim degilse null. */
        String article,
        String plural,
        String partOfSpeech,
        String meaningTr,
        Level level,
        String theme,
        String exampleDe,
        String exampleTr,
        int estimatedMinutes,
        boolean verified) {
}
