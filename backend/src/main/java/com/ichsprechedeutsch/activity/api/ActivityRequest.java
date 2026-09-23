package com.ichsprechedeutsch.activity.api;

import com.ichsprechedeutsch.activity.ActivityType;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.ResultSource;
import java.time.LocalDate;

/**
 * "Bugun ne yaptin?" formu (SPEC 6.2). Alanlarin cogu tek dokunuslu hazir
 * secenektir; not ve Sprechen konusu isteğe bagli kisa metindir (K-006).
 */
public record ActivityRequest(
        LocalDate date,
        ActivityType type,
        Integer durationMinutes,
        String sourceKind,
        Level level,
        Double score,
        Double maxScore,
        ResultSource resultSource,
        String speakingPartner,
        String speakingTopic,
        String grammarTopicId,
        String note) {
}
