package com.trackai.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RateLimitResponse {

        // REQUEST ALLOWED OR BLOCKED
        private boolean allowed;

        // REMAINING ATTEMPTS
        private long remainingAttempts;

        // RETRY AFTER (SECONDS)
        private long retryAfterSeconds;

        // TEMPORARY BLOCK STATUS
        private boolean temporaryBlocked;

        // RESPONSE MESSAGE
        private String message;
}