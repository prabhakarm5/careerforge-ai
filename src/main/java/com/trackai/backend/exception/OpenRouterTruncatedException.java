package com.trackai.backend.exception;

/**
 * Thrown when an AI provider's stream ends because it hit the max_tokens
 * limit (finish_reason == "length") instead of a natural stop.
 *
 * This is NOT a network/application error — the request succeeded, the
 * model just ran out of room. We surface it through the same onError path
 * as real errors so ChatServiceImpl's existing fallback + continuation
 * logic (switch model, resend partial answer, keep writing) kicks in
 * automatically instead of silently handing the user a cut-off answer.
 */
public class OpenRouterTruncatedException extends RuntimeException {
    public OpenRouterTruncatedException(String message) {
        super(message);
    }
}