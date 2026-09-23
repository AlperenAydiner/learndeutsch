package com.ichsprechedeutsch.coach;

import com.ichsprechedeutsch.activity.ActivityType;
import com.ichsprechedeutsch.common.model.Skill;
import java.util.Locale;
import java.util.Optional;

/**
 * Haftalik planin dagittigi calisma turleri (SPEC 5.4): dort beceri,
 * gramer ve kelime. Config'deki pay anahtarlari (coach.weekly-shares) bu
 * turlerin kucuk harfli adlaridir.
 */
public enum StudyType {
    KELIME("Kelime", null),
    GRAMER("Gramer", null),
    HOEREN("Hören", Skill.HOEREN),
    LESEN("Lesen", Skill.LESEN),
    SPRECHEN("Sprechen", Skill.SPRECHEN),
    SCHREIBEN("Schreiben", Skill.SCHREIBEN);

    private final String label;
    private final Skill skill;

    StudyType(String label, Skill skill) {
        this.label = label;
        this.skill = skill;
    }

    public String label() {
        return label;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Optional<Skill> skill() {
        return Optional.ofNullable(skill);
    }

    public static StudyType of(Skill skill) {
        return switch (skill) {
            case LESEN -> LESEN;
            case HOEREN -> HOEREN;
            case SCHREIBEN -> SCHREIBEN;
            case SPRECHEN -> SPRECHEN;
        };
    }

    /** Aktivitenin plandaki karsiligi; artikel kelimeye sayilir, seviye testi hicbirine. */
    public static Optional<StudyType> of(ActivityType type) {
        return Optional.ofNullable(switch (type) {
            case KELIME, ARTIKEL -> KELIME;
            case GRAMER -> GRAMER;
            case HOEREN -> HOEREN;
            case LESEN -> LESEN;
            case SPRECHEN -> SPRECHEN;
            case SCHREIBEN -> SCHREIBEN;
            case SEVIYE_TESTI -> null;
        });
    }
}
