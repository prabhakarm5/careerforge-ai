package com.trackai.backend.service.impl;

import com.trackai.backend.exception.TokenExpiredEXception;
import com.trackai.backend.security.JwtUtil;
import com.trackai.backend.service.RedisRefreshTokenService;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedisRefreshTokenServiceImpl
                implements RedisRefreshTokenService {

        private static final Logger log = LoggerFactory.getLogger(RedisRefreshTokenServiceImpl.class);

        private static final String PREFIX = "refresh_token:";

        private final StringRedisTemplate redisTemplate;
        private final JwtUtil jwtUtil;

        // =========================================
        // HELPERS
        // =========================================

        private String normalizeEmail(String email) {
                return email.trim().toLowerCase();
        }

        private String normalizeFingerprint(String fingerprint) {
                return fingerprint.trim().toLowerCase();
        }

        private String generateKey(String email, String fingerprint) {
                return PREFIX
                                + normalizeEmail(email)
                                + ":"
                                + normalizeFingerprint(fingerprint);
        }

        // =========================================
        // SAVE REFRESH TOKEN
        // =========================================

        @Override
        public void saveRefreshToken(
                        String email, String fingerprint, String refreshToken) {

                String key = generateKey(email, fingerprint);

                Date expiryDate = jwtUtil.extractExpiration(refreshToken);
                long ttl = expiryDate.getTime() - System.currentTimeMillis();

                if (ttl <= 0) {
                        throw new TokenExpiredEXception("Refresh token already expired");
                }

                redisTemplate.opsForValue().set(key, refreshToken, Duration.ofMillis(ttl));

                // ✅ FIX — Key log nahi, sirf action log karo
                log.debug("Refresh token saved for user: {}", normalizeEmail(email));
        }

        // =========================================
        // VALIDATE REFRESH TOKEN
        // =========================================

        @Override
        public boolean isValidRefreshToken(
                        String email, String fingerprint, String refreshToken) {

                String key = generateKey(email, fingerprint);
                String storedToken = redisTemplate.opsForValue().get(key);

                // Token Redis mein nahi — invalid
                if (storedToken == null) {
                        return false;
                }

                // Token match nahi — possible token reuse attack
                if (!storedToken.equals(refreshToken)) {
                        log.warn("Refresh token mismatch for user: {} — possible reuse attack",
                                        normalizeEmail(email));
                        return false;
                }

                // Fingerprint verify
                String tokenFingerprint = jwtUtil.extractFingerprint(refreshToken);
                return normalizeFingerprint(tokenFingerprint)
                                .equals(normalizeFingerprint(fingerprint));
        }

        // =========================================
        // DELETE SINGLE REFRESH TOKEN (logout)
        // =========================================

        @Override
        public void deleteRefreshToken(String email, String fingerprint) {
                String key = generateKey(email, fingerprint);
                redisTemplate.delete(key);
                log.debug("Refresh token deleted for user: {}", normalizeEmail(email));
        }

        // =========================================
        // DELETE ALL REFRESH TOKENS (logout all devices)
        // ✅ FIX — keys() ki jagah scan() use karo
        // =========================================

        @Override
        public void deleteAllRefreshTokens(String email) {
                String normalizedEmail = normalizeEmail(email);
                String pattern = PREFIX + normalizedEmail + ":*";

                // ✅ SCAN use karo — keys() production mein Redis block karta hai
                Set<String> keys = new java.util.HashSet<>();
                redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                        org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.scan(
                                        org.springframework.data.redis.core.ScanOptions.scanOptions()
                                                        .match(pattern)
                                                        .count(100)
                                                        .build());
                        while (cursor.hasNext()) {
                                keys.add(new String(cursor.next()));
                        }
                        return null;
                });

                if (keys.isEmpty()) {
                        log.debug("No active sessions found for user: {}", normalizedEmail);
                        return;
                }

                Long deletedCount = redisTemplate.delete(keys);
                log.info("All sessions cleared for user: {} — {} tokens removed",
                                normalizedEmail, deletedCount);
        }
}