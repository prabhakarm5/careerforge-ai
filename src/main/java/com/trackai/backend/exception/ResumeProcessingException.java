package com.trackai.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ResumeProcessingException extends RuntimeException {

    private final HttpStatus status;

    public ResumeProcessingException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public ResumeProcessingException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
