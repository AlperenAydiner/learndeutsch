package com.ichsprechedeutsch.content;

import com.ichsprechedeutsch.common.model.Level;
import java.util.List;

/**
 * Sprechen gorevi (SPEC 7). Site gercek konusma degerlendirmesi
 * YAPMAZ (K4): kullanici konusur, kendini kaydeder ve Kann-Beschreibung
 * oz degerlendirmesiyle isaretler (4.2).
 */
public record SpeakingTask(
        String id,
        Level level,
        String titleTr,
        String taskTr,
        /** Konusurken kullanilacak yonlendirici sorular. */
        List<String> promptsDe,
        List<String> phrases,
        int estimatedMinutes,
        boolean verified) {
}
