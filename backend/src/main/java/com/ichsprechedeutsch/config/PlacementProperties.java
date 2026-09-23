package com.ichsprechedeutsch.config;

import com.ichsprechedeutsch.common.model.Level;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Adaptif yerlestirme testi (SPEC 4.1, Ek A; blok boyutu K-003). */
@ConfigurationProperties(prefix = "placement")
public record PlacementProperties(
        Level startLevel,
        int blockSize,
        double passThreshold,
        double upThreshold,
        int maxQuestions) {
}
