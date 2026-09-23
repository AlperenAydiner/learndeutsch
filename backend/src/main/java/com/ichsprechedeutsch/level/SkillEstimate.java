package com.ichsprechedeutsch.level;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.Skill;
import java.util.List;

/**
 * Bir becerinin tahmini (SPEC 4.3, 4.4). Hangi kanitlara dayandigini
 * tasir (K3).
 *
 * @param kind  AT: "A2 civarı"; AT_LEAST: "en az A2" (ustunde hic deneme yok);
 *              BELOW: "B1'in altında" (level = gecilemeyen en dusuk seviye);
 *              NONE: veri yok
 * @param level kind'a gore tahmin seviyesi ya da gecilemeyen seviye
 * @param basis tahminde kullanilan kanitlar (pencere icindekiler)
 */
public record SkillEstimate(
        Skill skill,
        Kind kind,
        Level level,
        Confidence confidence,
        int evidenceCount,
        List<Evidence> basis) {

    public enum Kind { NONE, AT, AT_LEAST, BELOW }

    /** Beceri durumu: "Veri yok" / "Tahmini (dusuk guven)" / "Guvenilir". */
    public enum Confidence { NO_DATA, LOW, RELIABLE }

    public static SkillEstimate none(Skill skill) {
        return new SkillEstimate(skill, Kind.NONE, null, Confidence.NO_DATA, 0, List.of());
    }

    public boolean hasData() {
        return kind != Kind.NONE;
    }

    /**
     * Siralama ve karsilastirma icin seviye. BELOW'da gecilemeyen seviyenin
     * bir alti kullanilir ("B1'in altinda" -> A2; "A1'in altinda" -> A0).
     */
    public Level rank() {
        return switch (kind) {
            case NONE -> null;
            case BELOW -> level.previous();
            case AT, AT_LEAST -> level;
        };
    }
}
