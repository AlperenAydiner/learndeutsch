package com.ichsprechedeutsch.content;

import com.ichsprechedeutsch.common.model.Level;
import java.util.List;

/**
 * Soru. Ogrenme sorulari ({seviye}/questions.json) ile yerlestirme sorulari
 * ({seviye}/placement.json) ayri dosyalardadir (SPEC 8.5).
 */
public record Question(
        String id,
        Level level,
        String type,
        String prompt,
        List<String> options,
        String answer,
        List<String> acceptedAnswers,
        List<String> tags,
        String explanationTr,
        int estimatedMinutes,
        boolean verified,
        /** Yalniz yerlestirme sorularinda: GRAMMAR / VOCABULARY / READING. */
        String placementKind) {
}
