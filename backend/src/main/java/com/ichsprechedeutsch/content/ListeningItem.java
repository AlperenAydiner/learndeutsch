package com.ichsprechedeutsch.content;

import com.ichsprechedeutsch.common.model.Level;
import java.util.List;

/**
 * Hören alistirmasi (SPEC 7). Metin tarayicinin Web Speech API'siyle
 * (de-DE) seslendirilir; ses sentetik oldugu icin sonuc BECERI KANITI
 * DEGILDIR, ogrenme aktivitesidir (4.2).
 *
 * @param kind DICTATION (duydugunu yaz) ya da COMPREHENSION (sorular)
 */
public record ListeningItem(
        String id,
        Level level,
        String kind,
        String titleTr,
        String text,
        String textTr,
        int estimatedMinutes,
        List<ReadingText.Item> questions,
        boolean verified) {
}
