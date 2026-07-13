package com.trackai.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PromoCodeException extends RuntimeException {
    private final HttpStatus status;

    public PromoCodeException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}