package com.ichsprechedeutsch.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kelime tekrari (SPEC 8.1, Ek A). Faz 3a'da kullanilir. */
@ConfigurationProperties(prefix = "srs")
public record SrsProperties(
        List<Integer> intervalsDays,
        int strongWordDays,
        Map<Integer, Integer> dailyNewByMinutes,
        int dailyReviewLimit) {
}
