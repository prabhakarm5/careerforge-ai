package com.trackai.backend.service;

import com.trackai.backend.dto.RateLimitResponse;

public interface RedisRateLimitService {

    /**
     * Check rate limit for request
     */
    RateLimitResponse allowRequest(
            String key,
            long capacity,
            long refillTokens,
            long refillMinutes);
}