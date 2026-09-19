package com.ichsprechedeutsch.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Tum hata yanitlarinin ortak bicimi. Mesajlar Turkce ve kullaniciya
 * gosterilebilir olmalidir; ic detay (stack trace, SQL) asla sizdirilmaz.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        Map<String, String> fields,
        OffsetDateTime timestamp) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, null, OffsetDateTime.now());
    }

    public static ApiError of(int status, String error, String message,
                              Map<String, String> fields) {
        return new ApiError(status, error, message, fields, OffsetDateTime.now());
    }
}
