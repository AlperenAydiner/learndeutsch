package com.ichsprechedeutsch.skills;

import com.ichsprechedeutsch.config.LevelProperties;
import com.ichsprechedeutsch.config.LevelProperties.CanDoAnswer;
import java.util.Map;

/**
 * Kann-Beschreibungen oz degerlendirmesi (SPEC 4.2): cevaplarin puan
 * ortalamasi. Puanlar config'de (Ek A: evet 1, kismen 0.5, hayir 0). Saf.
 */
public final class CanDoScore {

    public record Result(double score, double maxScore, double ratio, int answered) {
    }

    private CanDoScore() {
    }

    public static Result of(Map<String, CanDoAnswer> answers, LevelProperties config) {
        if (answers == null || answers.isEmpty()) {
            return new Result(0, 0, 0, 0);
        }
        double toplam = 0;
        for (CanDoAnswer a : answers.values()) {
            toplam += config.canDoScores().getOrDefault(a, 0.0);
        }
        double azami = answers.size();
        return new Result(toplam, azami, toplam / azami, answers.size());
    }
}
