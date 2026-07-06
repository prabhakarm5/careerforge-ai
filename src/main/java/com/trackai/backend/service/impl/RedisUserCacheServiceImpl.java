package com.trackai.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.dto.cache.CachedUser;
import com.trackai.backend.enums.Role;
import com.trackai.backend.service.RedisUserCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisUserCacheServiceImpl
        implements RedisUserCacheService {

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    /*
     * ==========================================================
     * Redis Prefix
     * ==========================================================
     */
    private static final String USER_PREFIX = "user_cache:";

    /*
     * ==========================================================
     * Cache Expiry
     * ==========================================================
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /*
     * ==========================================================
     * Normalize Email
     * ==========================================================
     */
    private String normalizeEmail(String email) {

        return email
                .trim()
                .toLowerCase();
    }

    /*
     * ==========================================================
     * Save User
     * ==========================================================
     */
    @Override
    public void saveUser(CachedUser user) {

        if (user == null) {
            return;
        }

        /*
         * Never cache Admin
         */
        if (user.getRole() == Role.ROLE_ADMIN) {

            log.info("Admin user not cached : {}", user.getEmail());

            return;
        }

        try {

            String key = USER_PREFIX + normalizeEmail(user.getEmail());

            String json = objectMapper.writeValueAsString(user);

            redisTemplate.opsForValue().set(
                    key,
                    json,
                    CACHE_TTL);

            log.info("User cached successfully : {}", key);

        } catch (JsonProcessingException e) {

            log.error("Failed to serialize user cache", e);

        } catch (Exception e) {

            log.error("Redis save failed", e);

        }

    }

    /*
     * ==========================================================
     * Get User
     * ==========================================================
     */
    @Override
    public CachedUser getUser(String email) {

        try {

            String key = USER_PREFIX + normalizeEmail(email);

            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {

                log.info("Redis Cache MISS : {}", key);

                return null;
            }

            log.info("Redis Cache HIT : {}", key);

            return objectMapper.readValue(
                    json,
                    CachedUser.class);

        } catch (Exception e) {

            log.error("Redis read failed", e);

            return null;
        }
    }

    /*
     * ==========================================================
     * Delete Cache
     * ==========================================================
     */
    @Override
    public void deleteUser(String email) {

        try {

            String key = USER_PREFIX + normalizeEmail(email);

            redisTemplate.delete(key);

            log.info("User cache deleted : {}", key);

        } catch (Exception e) {

            log.error("Redis delete failed", e);

        }

    }

    /*
     * ==========================================================
     * Update Cache
     * ==========================================================
     */
    @Override
    public void updateUser(CachedUser user) {

        saveUser(user);

    }

}