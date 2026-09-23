package com.ichsprechedeutsch.srs;

/** Tekrar oturumunda kullanicinin verdigi not (SPEC 8.1). */
public enum Grade {
    /** Bilemedim: merdivenin basina doner. */
    BILEMEDIM("Bilemedim"),
    /** Zorlandim: ayni aralik tekrar. */
    ZORLANDIM("Zorlandım"),
    /** Bildim: bir ust aralik. */
    BILDIM("Bildim");

    private final String labelTr;

    Grade(String labelTr) {
        this.labelTr = labelTr;
    }

    public String labelTr() {
        return labelTr;
    }
}
