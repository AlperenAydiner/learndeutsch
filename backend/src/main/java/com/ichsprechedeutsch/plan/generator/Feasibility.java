package com.ichsprechedeutsch.plan.generator;

/**
 * Hedefin gerceklige uygunlugu.
 *
 * UNREALISTIC oldugunda da plan uretilir: kullaniciyi bos ekranla
 * birakmak yerine plani gosterip acikca uyarmak daha durust.
 */
public enum Feasibility {
    /** Is yuku zamana rahat siginiyor. */
    OK,
    /** Siginiyor ama bos gun yok; aksama toleransi dusuk. */
    TIGHT,
    /** Sigmiyor. Kullaniciya somut alternatif sunulur. */
    UNREALISTIC
}
