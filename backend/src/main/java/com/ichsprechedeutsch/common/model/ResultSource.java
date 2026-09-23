package com.ichsprechedeutsch.common.model;

/**
 * Bir sonucun nereden geldigi. Guvenilirlik kademesi buradan belirlenir
 * (SPEC 6.2, 4.4); eslesme config'dedir (level.source-tiers).
 */
public enum ResultSource {
    OFFICIAL_EXAM,
    TEACHER,
    MODELLTEST,
    APP_TEST,
    SELF_ASSESSMENT,
    /** Site ici otomatik puanli beceri testi. */
    SITE_TEST
}
