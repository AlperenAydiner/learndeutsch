package com.ichsprechedeutsch.common.error;

/** Istenen kayit yok ya da bu kullaniciya ait degil. 404 olarak doner. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
