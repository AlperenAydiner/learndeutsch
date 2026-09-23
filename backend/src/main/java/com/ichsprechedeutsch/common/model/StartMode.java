package com.ichsprechedeutsch.common.model;

/** Onboarding'in ilk sorusu (SPEC Bolum 3). */
public enum StartMode {
    /** "Almancaya sifirdan basliyorum" -> calisma seviyesi A0, test yok. */
    FROM_ZERO,
    /** "Bir miktar Almanca biliyorum" -> yerlestirme testi onerilir. */
    SOME_KNOWLEDGE
}
