package com.ichsprechedeutsch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Aktivite kaydi (SPEC Bolum 6, Ek A). */
@ConfigurationProperties(prefix = "activity")
public record ActivityProperties(
        /** Iki etkilesim arasindaki bundan uzun bosluk aktif sureye sayilmaz. */
        int idleGapMinutes,
        int maxNoteLength) {
}
