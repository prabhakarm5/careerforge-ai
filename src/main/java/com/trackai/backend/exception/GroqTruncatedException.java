package com.trackai.backend.exception;

/**
 * Thrown when Groq's stream ends because it hit the max_tokens limit
 * (finish_reason == "length") instead of a natural stop. Not a network
 * error — the request succeeded, the model just ran out of room. Routed
 * through onError so ChatServiceImpl's existing fallback + continuation
 * logic picks it up automatically.
 */
public class GroqTruncatedException extends RuntimeException {
    public GroqTruncatedException(String message) {
        super(message);
    }
}