package com.trackai.backend.service.impl;

import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.service.RedisRateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRateLimitServiceImpl implements RedisRateLimitService {
    private static final String PREFIX = "rate_limit:";

    // One server-side operation replaces GET, INCR, EXPIRE and GET TTL round-trips.
    private static final DefaultRedisScript<List> LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]); end; "
                    + "local ttl = redis.call('PTTL', KEYS[1]); "
                    + "if count > tonumber(ARGV[1]) then return {0, 0, ttl}; end; "
                    + "return {1, tonumber(ARGV[1]) - count, ttl};",
            List.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public RateLimitResponse allowRequest(String key, long capacity, long refillTokens, long refillMinutes) {
        if (capacity <= 0 || refillMinutes <= 0) {
            log.warn("Invalid rate-limit configuration for {}. Allowing request.", key);
            return allowed(Math.max(0, capacity), "Request allowed");
        }

        try {
            long windowMs = Duration.ofMinutes(refillMinutes).toMillis();
            List<?> result = redisTemplate.execute(
                    LIMIT_SCRIPT, List.of(PREFIX + key), String.valueOf(capacity), String.valueOf(windowMs));
            if (result == null || result.size() < 3) {
                log.warn("Redis returned no rate-limit result for {}. Allowing request.", key);
                return allowed(capacity, "Request allowed");
            }

            boolean isAllowed = number(result.get(0)) == 1;
            long remaining = Math.max(0, number(result.get(1)));
            long retryAfterSeconds = Math.max(1, (long) Math.ceil(number(result.get(2)) / 1000.0));
            if (!isAllowed) {
                return RateLimitResponse.builder()
                        .allowed(false).remainingAttempts(0).retryAfterSeconds(retryAfterSeconds)
                        .temporaryBlocked(true)
                        .message("Too many requests. Try again after " + retryAfterSeconds + " seconds")
                        .build();
            }

            String message = remaining <= 2
                    ? "Warning: Only " + remaining + " attempts remaining before temporary block."
                    : "Request allowed";
            return allowed(remaining, message);
        } catch (RuntimeException redisFailure) {
            // Rate limiting must not make the entire application unavailable when Redis has an outage.
            log.warn("Rate-limit check unavailable for {}: {}", key, redisFailure.getMessage());
            return allowed(capacity, "Request allowed");
        }
    }

    private RateLimitResponse allowed(long remaining, String message) {
        return RateLimitResponse.builder()
                .allowed(true).remainingAttempts(remaining).retryAfterSeconds(0)
                .temporaryBlocked(false).message(message).build();
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }
}