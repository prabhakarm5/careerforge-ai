package com.trackai.backend.service.impl;

import com.trackai.backend.exception.TokenExpiredEXception;
import com.trackai.backend.security.JwtUtil;
import com.trackai.backend.service.RedisRefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedisRefreshTokenServiceImpl implements RedisRefreshTokenService {

        private static final Logger log = LoggerFactory.getLogger(RedisRefreshTokenServiceImpl.class);
        private static final String PREFIX = "refresh_token:";
        private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>(
                        "local current = redis.call('GET', KEYS[1]); "
                                        + "if (not current) or current ~= ARGV[1] then return 0 end; "
                                        + "redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2]); return 1;",
                        Long.class);

        private final StringRedisTemplate redisTemplate;
        private final JwtUtil jwtUtil;

        private String normalizeEmail(String email) {
                return email.trim().toLowerCase();
        }

        private String normalizeFingerprint(String fingerprint) {
                return fingerprint.trim().toLowerCase();
        }

        private String generateKey(String email, String fingerprint) {
                return PREFIX + normalizeEmail(email) + ":" + normalizeFingerprint(fingerprint);
        }

        @Override
        public void saveRefreshToken(String email, String fingerprint, String refreshToken) {
                String key = generateKey(email, fingerprint);
                long ttl = remainingTtl(refreshToken);
                redisTemplate.opsForValue().set(key, refreshToken, Duration.ofMillis(ttl));
                log.debug("Refresh token saved for user: {}", normalizeEmail(email));
        }

        @Override
        public boolean isValidRefreshToken(String email, String fingerprint, String refreshToken) {
                String storedToken = redisTemplate.opsForValue().get(generateKey(email, fingerprint));
                if (storedToken == null) return false;
                if (!storedToken.equals(refreshToken)) {
                        log.warn("Refresh token mismatch for user: {} - possible reuse attack", normalizeEmail(email));
                        return false;
                }
                String tokenFingerprint = jwtUtil.extractFingerprint(refreshToken);
                return normalizeFingerprint(tokenFingerprint).equals(normalizeFingerprint(fingerprint));
        }

        @Override
        public boolean rotateRefreshToken(
                        String email,
                        String fingerprint,
                        String currentRefreshToken,
                        String newRefreshToken) {
                Long rotated = redisTemplate.execute(
                                ROTATE_SCRIPT,
                                Collections.singletonList(generateKey(email, fingerprint)),
                                currentRefreshToken,
                                newRefreshToken,
                                Long.toString(remainingTtl(newRefreshToken)));
                return Long.valueOf(1).equals(rotated);
        }

        @Override
        public void deleteRefreshToken(String email, String fingerprint) {
                redisTemplate.delete(generateKey(email, fingerprint));
                log.debug("Refresh token deleted for user: {}", normalizeEmail(email));
        }

        @Override
        public void deleteAllRefreshTokens(String email) {
                String normalizedEmail = normalizeEmail(email);
                String pattern = PREFIX + normalizedEmail + ":*";
                Set<String> keys = new java.util.HashSet<>();
                redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                        try (org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.scan(
                                        org.springframework.data.redis.core.ScanOptions.scanOptions()
                                                        .match(pattern).count(100).build())) {
                                while (cursor.hasNext()) keys.add(new String(cursor.next()));
                        }
                        return null;
                });
                if (keys.isEmpty()) {
                        log.debug("No active sessions found for user: {}", normalizedEmail);
                        return;
                }
                Long deletedCount = redisTemplate.delete(keys);
                log.info("All sessions cleared for user: {} - {} tokens removed", normalizedEmail, deletedCount);
        }

        private long remainingTtl(String refreshToken) {
                Date expiryDate = jwtUtil.extractExpiration(refreshToken);
                long ttl = expiryDate.getTime() - System.currentTimeMillis();
                if (ttl <= 0) throw new TokenExpiredEXception("Refresh token already expired");
                return ttl;
        }
}