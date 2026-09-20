package com.ichsprechedeutsch.task.api;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Gorev isaretleme.
 *
 * @param status     DONE | PARTIAL | MISSED
 * @param selfReport gorev tipine ozel hazir secenekler; bos olabilir.
 *                   Tanimsiz alan veya deger sunucuda reddedilir.
 */
public record CompleteTaskRequest(

        @NotBlank(message = "Durum secilmeli")
        String status,

        Map<String, String> selfReport) {
}
