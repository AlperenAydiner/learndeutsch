package com.ichsprechedeutsch.common.error;

/**
 * Istek bicimsel olarak dogru ama is kurallarina aykiri. 400 olarak doner.
 * (Alan seviyesi dogrulamalar icin jakarta.validation kullanilir.)
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
