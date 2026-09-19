package com.ichsprechedeutsch.common.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adim 0 dogrulama ucu: uygulama ayakta mi?
 * Guvenlik yapilandirmasinda herkese acik birakilir.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "ichsprechedeutsch-backend",
                "time", Instant.now().toString());
    }
}
