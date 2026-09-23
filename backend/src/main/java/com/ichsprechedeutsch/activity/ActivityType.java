package com.ichsprechedeutsch.activity;

import com.ichsprechedeutsch.common.model.Skill;
import java.util.Optional;

/**
 * Aktivite turu. Ilk alti "Bugun ne yaptin?" formunda secilebilir
 * (SPEC 6.2); ARTIKEL ve SEVIYE_TESTI yalniz site ici otomatik kayittir.
 */
public enum ActivityType {
    HOEREN(Skill.HOEREN),
    LESEN(Skill.LESEN),
    SCHREIBEN(Skill.SCHREIBEN),
    SPRECHEN(Skill.SPRECHEN),
    GRAMER(null),
    KELIME(null),
    ARTIKEL(null),
    SEVIYE_TESTI(null);

    private final Skill skill;

    ActivityType(Skill skill) {
        this.skill = skill;
    }

    /** Dort beceriden biriyse o beceri (K1); ogrenme aktivitesiyse bos (K2). */
    public Optional<Skill> skill() {
        return Optional.ofNullable(skill);
    }

    public boolean userSelectable() {
        return this != ARTIKEL && this != SEVIYE_TESTI;
    }
}
