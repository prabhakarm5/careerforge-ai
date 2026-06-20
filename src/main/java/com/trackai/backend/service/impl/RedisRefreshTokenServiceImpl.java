package com.trackai.backend.service.impl;

import com.trackai.backend.security.JwtUtil;
import com.trackai.backend.service.RedisRefreshTokenService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedisRefreshTokenServiceImpl
                implements RedisRefreshTokenService {

        private static final String PREFIX = "refresh_token:";

        private final StringRedisTemplate redisTemplate;
        private final JwtUtil jwtUtil;

        // Normalize email
        private String normalizeEmail(String email) {

                return email.trim().toLowerCase();
        }

        // Normalize fingerprint
        private String normalizeFingerprint(String fingerprint) {

                return fingerprint.trim().toLowerCase();
        }

        // Generate Redis key
        private String generateKey(
                        String email,
                        String fingerprint) {

                return PREFIX
                                + normalizeEmail(email)
                                + ":"
                                + normalizeFingerprint(fingerprint);
        }

        // Save refresh token
        @Override
        public void saveRefreshToken(
                        String email,
                        String fingerprint,
                        String refreshToken) {

                String key = generateKey(email, fingerprint);

                Date expiryDate = jwtUtil.extractExpiration(refreshToken);

                long ttl = expiryDate.getTime()
                                - System.currentTimeMillis();

                if (ttl <= 0) {

                        throw new RuntimeException(
                                        "Refresh token already expired");
                }

                redisTemplate.opsForValue().set(
                                key,
                                refreshToken,
                                Duration.ofMillis(ttl));

                System.out.println(
                                "REFRESH TOKEN SAVED : "
                                                + key);
        }

        // Validate refresh token
        @Override
        public boolean isValidRefreshToken(
                        String email,
                        String fingerprint,
                        String refreshToken) {

                String key = generateKey(email, fingerprint);

                String storedToken = redisTemplate.opsForValue().get(key);

                if (storedToken == null) {

                        return false;
                }

                if (!storedToken.equals(refreshToken)) {

                        return false;
                }

                String tokenFingerprint = jwtUtil.extractFingerprint(refreshToken);

                return normalizeFingerprint(tokenFingerprint)
                                .equals(normalizeFingerprint(fingerprint));
        }

        // Delete single refresh token
        @Override
        public void deleteRefreshToken(
                        String email,
                        String fingerprint) {

                String key = generateKey(email, fingerprint);

                redisTemplate.delete(key);

                System.out.println(
                                "REFRESH TOKEN DELETED : "
                                                + key);
        }

        // Delete all refresh tokens
        @Override
        public void deleteAllRefreshTokens(String email) {

                email = normalizeEmail(email);

                String pattern = PREFIX + email + ":*";

                Set<String> keys = redisTemplate.keys(pattern);

                if (keys == null || keys.isEmpty()) {

                        System.out.println(
                                        "NO ACTIVE SESSIONS FOUND : "
                                                        + email);

                        return;
                }

                Long deletedCount = redisTemplate.delete(keys);

                System.out.println(
                                "ALL REFRESH TOKENS DELETED : "
                                                + email);

                System.out.println(
                                "TOTAL TOKENS REMOVED : "
                                                + deletedCount);
        }
}