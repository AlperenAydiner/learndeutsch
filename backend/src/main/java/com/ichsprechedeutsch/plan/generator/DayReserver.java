package com.ichsprechedeutsch.plan.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Takvimde tekrar gunlerini ayirir.
 *
 * Haftada bir hafif gun: yeni konu yok, tekrar var. Bu bir luks degil -
 * tekrara yer birakmayan program ogrenileni tutamaz.
 *
 * Sinav provasi, son tekrar ve denemeler burada YERLESTIRILMEZ. Onlar
 * icerik havuzunda kendi birimleri olarak durur ve dagitici onlari
 * sondan geriye yerlestirir. Boylece "sinav gunu sabit" sozu tek bir
 * yerde, gercek icerikle tutulur.
 */
public class DayReserver {

    public static final String NORMAL = "NORMAL";
    public static final String LIGHT = "LIGHT";
    public static final String MOCK_EXAM = "MOCK_EXAM";

    /** Kac gunde bir hafif gun gelir. */
    private static final int LIGHT_EVERY = 7;

    /** Ogretim gunleri toplamin en az bu kadari olmali. */
    private static final double MIN_TEACHING_SHARE = 0.6;

    /**
     * @return her gun icin gun tipi; dizi indeksi 0 = 1. gun
     */
    public String[] reserve(int totalDays) {
        String[] types = new String[totalDays];
        Arrays.fill(types, NORMAL);

        for (int i = LIGHT_EVERY - 1; i < totalDays; i += LIGHT_EVERY) {
            types[i] = LIGHT;
        }

        // Son gun asla hafif gun olmaz: sinav provasi oraya yerlesecek.
        // Haftalik ritim sinav gununu yutmamali.
        types[totalDays - 1] = NORMAL;

        ensureEnoughTeachingDays(types);
        return types;
    }

    /**
     * Cok kisa planlarda hafif gunler ogretime fazla yer birakmayabilir.
     * Boyle bir durumda sondan basa dogru geri alinir: yeni konu gormeden
     * hedefe varilmaz.
     */
    private void ensureEnoughTeachingDays(String[] types) {
        int minTeaching = (int) Math.ceil(types.length * MIN_TEACHING_SHARE);

        for (int i = types.length - 1; i >= 0 && count(types, NORMAL) < minTeaching; i--) {
            if (LIGHT.equals(types[i])) {
                types[i] = NORMAL;
            }
        }
    }

    /** Ogretim gunlerinin indeksleri, sirali. */
    public List<Integer> teachingDays(String[] types) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            if (NORMAL.equals(types[i])) {
                out.add(i);
            }
        }
        return out;
    }

    private int count(String[] types, String type) {
        return (int) Arrays.stream(types).filter(type::equals).count();
    }
}
