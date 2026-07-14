package com.trackai.backend.dto.admin;

import java.time.Instant;
import java.util.Map;

public record AdminSystemStatusResponse(
        Instant checkedAt,
        String overallStatus,
        Map<String, ComponentStatus> services,
        Map<String, Boolean> integrations,
        Map<String, RateLimitStatus> rateLimits) {

    public record ComponentStatus(String status, long latencyMs, String message) {
    }

    public record RateLimitStatus(long capacity, long refillTokens, long refillMinutes) {
    }
}