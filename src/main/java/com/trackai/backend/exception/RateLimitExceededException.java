package com.trackai.backend.exception;

// Thrown when a user exceeds allowed rate limit for an action
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}