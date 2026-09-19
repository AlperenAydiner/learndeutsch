package com.ichsprechedeutsch.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tum hatalari tek bicimde yanita cevirir.
 *
 * Kural: istemciye yalnizca ne yapmasi gerektigini soyleyen bir mesaj gider.
 * Beklenmeyen hatalarin detayi sunucu log'una yazilir, yanita konmaz.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ApiError> handleValidation(ValidationException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "VALIDATION_ERROR", e.getMessage()));
    }

    /** Alan seviyesi dogrulama: hangi alanin neden reddedildigini de doner. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ApiError.of(
                400, "VALIDATION_ERROR", "Gonderilen bilgiler gecerli degil", fields));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuth(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "UNAUTHORIZED", "Oturum gecersiz, tekrar giris yap"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "FORBIDDEN", "Bu islem icin yetkin yok"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("Beklenmeyen hata", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "INTERNAL_ERROR",
                        "Beklenmeyen bir hata olustu, lutfen tekrar dene"));
    }
}
