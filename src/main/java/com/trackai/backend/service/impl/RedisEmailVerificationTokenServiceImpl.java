package com.trackai.backend.service.impl;

import com.trackai.backend.service.RedisEmailVerificationTokenService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisEmailVerificationTokenServiceImpl

                implements RedisEmailVerificationTokenService {

        private final StringRedisTemplate redisTemplate;

        private static final String EMAIL_PREFIX =

                        "email_verification_token:";

        private static final String LOOKUP_PREFIX =

                        "email_verification_lookup:";

        private static final String RESEND_PREFIX = "email_resend_cooldown:";

        // NORMALIZE EMAIL
        private String normalizeEmail(
                        String email) {

                return email

                                .trim()

                                .toLowerCase();
        }

        // SAVE VERIFICATION TOKEN
        @Override
        public void saveVerificationToken(

                        String email,

                        String token) {

                // NORMALIZE EMAIL
                email = normalizeEmail(email);

                // REMOVE OLD TOKEN
                String oldToken =

                                redisTemplate.opsForValue()

                                                .get(EMAIL_PREFIX + email);

                // DELETE OLD LOOKUP
                if (oldToken != null) {

                        redisTemplate.delete(
                                        LOOKUP_PREFIX + oldToken);
                }

                // SAVE EMAIL -> TOKEN
                redisTemplate.opsForValue()

                                .set(

                                                EMAIL_PREFIX + email,

                                                token,

                                                Duration.ofMinutes(15));

                // SAVE TOKEN -> EMAIL
                redisTemplate.opsForValue()

                                .set(

                                                LOOKUP_PREFIX + token,

                                                email,

                                                Duration.ofMinutes(15));
        }

        // GET EMAIL BY TOKEN
        @Override
        public String getEmailByToken(
                        String token) {

                return redisTemplate.opsForValue()

                                .get(LOOKUP_PREFIX + token);
        }

        // DELETE TOKEN
        @Override
        public void deleteToken(
                        String token) {

                // GET EMAIL
                String email =

                                redisTemplate.opsForValue()

                                                .get(LOOKUP_PREFIX + token);

                // DELETE LOOKUP
                redisTemplate.delete(
                                LOOKUP_PREFIX + token);

                // DELETE EMAIL TOKEN
                if (email != null) {

                        redisTemplate.delete(
                                        EMAIL_PREFIX + email);
                }
        }

        @Override
        public void saveResendCooldown(
                        String email) {

                email = normalizeEmail(email);

                redisTemplate.opsForValue()

                                .set(

                                                RESEND_PREFIX + email,

                                                "true",

                                                Duration.ofMinutes(5)

                                );
        }

        @Override
        public boolean hasResendCooldown(
                        String email) {

                email = normalizeEmail(email);

                return Boolean.TRUE.equals(

                                redisTemplate.hasKey(

                                                RESEND_PREFIX + email

                                )

                );
        }

        @Override
        public Long getResendCooldownSeconds(
                        String email) {

                email = normalizeEmail(email);

                return redisTemplate.getExpire(

                                RESEND_PREFIX + email

                );
        }
}