package com.ichsprechedeutsch.common.model;

/**
 * Seviyeler. A0 yalnizca CALISMA seviyesi olarak kullanilir (SPEC 4.6);
 * bir seviye iddiasi degildir. Beceri tahminleri A1-C1 arasindadir.
 */
public enum Level {
    A0, A1, A2, B1, B2, C1;

    public boolean isAtLeast(Level other) {
        return compareTo(other) >= 0;
    }

    /**
     * Icerik secimi icin seviye: A0 olan kullanici A1 icerigiyle calisir.
     * "A0" bir icerik seviyesi degil, "henuz A1 degil" demektir (SPEC 4.6).
     */
    public Level forContent() {
        return this == A0 ? A1 : this;
    }

    /** Bir ust seviye; C1'de C1 kalir. */
    public Level next() {
        return this == C1 ? C1 : values()[ordinal() + 1];
    }

    /** Bir alt seviye; A0'da A0 kalir. */
    public Level previous() {
        return this == A0 ? A0 : values()[ordinal() - 1];
    }

    /** Degerlendirilebilir seviyeler (A1-C1). */
    public static Level[] assessable() {
        return new Level[]{A1, A2, B1, B2, C1};
    }
}
