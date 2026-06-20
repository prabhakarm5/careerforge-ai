package com.trackai.backend.service.impl;

import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.service.RedisRateLimitService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisRateLimitServiceImpl implements RedisRateLimitService {

        private static final String PREFIX = "rate_limit:";

        private final StringRedisTemplate redisTemplate;

        @Override
        public RateLimitResponse allowRequest(
                        String key,
                        long capacity,
                        long refillTokens,
                        long refillMinutes) {

                String redisKey = PREFIX + key;

                // Current request count
                String storedValue = redisTemplate.opsForValue().get(redisKey);

                long currentCount = storedValue == null
                                ? 0
                                : Long.parseLong(storedValue);

                // Rate limit exceeded
                if (currentCount >= capacity) {

                        Long ttl = redisTemplate.getExpire(redisKey);

                        long retryAfter = ttl == null ? 0 : ttl;

                        System.out.println(
                                        "RATE LIMIT EXCEEDED : " + redisKey);

                        return RateLimitResponse.builder()
                                        .allowed(false)
                                        .remainingAttempts(0)
                                        .retryAfterSeconds(retryAfter)
                                        .temporaryBlocked(true)
                                        .message(
                                                        "Too many requests. Try again after "
                                                                        + retryAfter
                                                                        + " seconds")
                                        .build();
                }

                // Increment request count
                Long updatedCount = redisTemplate.opsForValue().increment(redisKey);

                // First request → set expiry
                if (updatedCount != null && updatedCount == 1) {

                        redisTemplate.expire(
                                        redisKey,
                                        Duration.ofMinutes(refillMinutes));
                }

                long remainingAttempts = Math.max(0, capacity - updatedCount);

                // Generate response message
                String message;

                if (remainingAttempts == 0) {

                        message = "Last allowed request reached. Next request will temporarily block this endpoint.";

                } else if (remainingAttempts <= 2) {

                        message = "Warning: Only "
                                        + remainingAttempts
                                        + " attempts remaining before temporary block.";

                } else {

                        message = "Request allowed";
                }
                System.out.println("KEY = " + redisKey);

                System.out.println("COUNT = " + updatedCount);

                System.out.println("TTL = " + redisTemplate.getExpire(redisKey));

                return RateLimitResponse.builder()
                                .allowed(true)
                                .remainingAttempts(remainingAttempts)
                                .retryAfterSeconds(0)
                                .temporaryBlocked(false)
                                .message(message)
                                .build();
        }
}