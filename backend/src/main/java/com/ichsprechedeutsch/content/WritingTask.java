package com.ichsprechedeutsch.content;

import com.ichsprechedeutsch.common.model.Level;
import java.util.List;

/**
 * Schreiben gorevi (SPEC 7). Metin AI olmadan puanlanamaz: kullanici
 * kendi metnini rubrikle degerlendirir, ornek cevapla karsilastirir
 * (4.2, 8.2). Rubrik olcut ve puanlari config'den gelir (Ek A).
 */
public record WritingTask(
        String id,
        Level level,
        String titleTr,
        String taskTr,
        String taskDe,
        int minWords,
        /** Kullanilabilecek hazir kaliplar. */
        List<String> phrases,
        String sampleAnswer,
        int estimatedMinutes,
        boolean verified) {
}
