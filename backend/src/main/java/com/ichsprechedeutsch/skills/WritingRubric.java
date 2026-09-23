package com.ichsprechedeutsch.skills;

import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.config.LevelProperties;
import java.util.List;
import java.util.Map;

/**
 * Schreiben rubrigi (SPEC 4.2): kullanici kendi metnini olcutlere gore
 * puanlar. Olcut sayisi ve olcut basina azami puan config'den gelir
 * (Ek A: 4 olcut x 3 puan). Saf.
 *
 * <p>Serbest metin AI olmadan puanlanamaz (8.2); bu yuzden puani
 * kullanici verir, sistem yalniz orani hesaplar ve kanit kademesini
 * "oz degerlendirme" olarak isaretler.
 */
public final class WritingRubric {

    public record Result(double score, double maxScore, double ratio) {
    }

    private WritingRubric() {
    }

    public static List<String> criteria(LevelProperties config) {
        List<String> adlar = config.schreibenRubric().criteriaNames();
        if (adlar == null || adlar.size() != config.schreibenRubric().criteria()) {
            throw new IllegalStateException("Rubrik olcut adlari config ile uyusmuyor");
        }
        return adlar;
    }

    public static Result score(Map<String, Integer> scores, LevelProperties config) {
        List<String> olcutler = criteria(config);
        int azamiTek = config.schreibenRubric().maxPerCriterion();
        if (scores == null || scores.size() != olcutler.size()) {
            throw new ValidationException("Her ölçüt için bir puan ver");
        }
        double toplam = 0;
        for (String olcut : olcutler) {
            Integer puan = scores.get(olcut);
            if (puan == null || puan < 0 || puan > azamiTek) {
                throw new ValidationException("Puan 0 ile " + azamiTek + " arasında olmalı: " + olcut);
            }
            toplam += puan;
        }
        double azami = olcutler.size() * (double) azamiTek;
        return new Result(toplam, azami, toplam / azami);
    }
}
