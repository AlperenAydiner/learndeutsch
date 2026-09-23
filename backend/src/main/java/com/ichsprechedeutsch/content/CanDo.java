package com.ichsprechedeutsch.content;

import com.ichsprechedeutsch.common.model.Skill;
import com.ichsprechedeutsch.common.model.Level;

/**
 * "Kann-Beschreibung": seviye bazli yapabilirlik ifadesi (SPEC 4.2).
 * Resmi CEFR tanimlayicilari kopyalanmaz; ifadeler kendi sozumuzle
 * yazilir (10.2).
 */
public record CanDo(
        String id,
        Level level,
        Skill skill,
        String textTr,
        boolean verified) {
}
