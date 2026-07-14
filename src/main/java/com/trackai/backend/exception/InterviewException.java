package com.trackai.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class InterviewException extends RuntimeException {
    private final HttpStatus status;

    public InterviewException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public InterviewException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
