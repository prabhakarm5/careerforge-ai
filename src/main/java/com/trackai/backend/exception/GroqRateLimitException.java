package com.trackai.backend.exception;

/**
 * Thrown when Groq's API returns HTTP 429 (rate limited / model busy).
 * Caught in ChatServiceImpl to trigger an automatic fallback to another
 * configured model.
 */
public class GroqRateLimitException extends RuntimeException {
    public GroqRateLimitException(String message) {
        super(message);
    }
}