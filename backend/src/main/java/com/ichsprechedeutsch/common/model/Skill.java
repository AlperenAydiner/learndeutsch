package com.ichsprechedeutsch.common.model;

/** Gercek dil seviyesini olusturan dort beceri (K1). */
public enum Skill {
    LESEN("Lesen"),
    HOEREN("Hören"),
    SCHREIBEN("Schreiben"),
    SPRECHEN("Sprechen");

    private final String label;

    Skill(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
