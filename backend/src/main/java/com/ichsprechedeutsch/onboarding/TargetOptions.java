package com.ichsprechedeutsch.onboarding;

import com.ichsprechedeutsch.common.model.Level;
import java.util.ArrayList;
import java.util.List;

/**
 * Onboarding'de sunulan hedef seviyeler (SPEC Bolum 3, adim 3): test
 * yapildiysa tahminin ustundekiler (tahmin C1 ise C1), yapilmadiysa hepsi.
 * Ayrica hedefe giden kisisel yol (or. A1 -> A2 -> B1).
 */
public final class TargetOptions {

    private TargetOptions() {
    }

    /** @param placement yerlestirme sonucu; yoksa null. A0 = "A1'in altinda". */
    public static List<Level> offered(Level placement) {
        List<Level> out = new ArrayList<>();
        for (Level l : Level.assessable()) {
            if (placement == null || l.compareTo(placement) > 0) {
                out.add(l);
            }
        }
        if (out.isEmpty()) {
            out.add(Level.C1);
        }
        return out;
    }

    /** Calisma seviyesinden hedefe kadar seviyeler; A0'dan baslayan yol A1'le baslar. */
    public static List<Level> path(Level from, Level target) {
        List<Level> out = new ArrayList<>();
        Level start = from == Level.A0 ? Level.A1 : from;
        for (Level l : Level.assessable()) {
            if (l.compareTo(start) >= 0 && l.compareTo(target) <= 0) {
                out.add(l);
            }
        }
        if (out.isEmpty()) {
            out.add(target);
        }
        return out;
    }
}
