package com.trackai.backend.service.impl;

import com.trackai.backend.service.RedisForgotPasswordOtpService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RedisForgotPasswordOtpServiceImpl
                implements RedisForgotPasswordOtpService {

        private static final String OTP_PREFIX = "forgot_password_otp:";
        private static final String RESEND_PREFIX = "forgot_password_resend:";

        // token -> email (NOT email -> flag, so it can't be guessed/forged from email)
        private static final String RESET_TOKEN_PREFIX = "forgot_password_reset_token:";

        private static final SecureRandom secureRandom = new SecureRandom();

        private final StringRedisTemplate redisTemplate;

        @Value("${app.forgot-password.otp-expiry-minutes}")
        private long forgotPasswordOtpExpiryMinutes;

        @Value("${app.forgot-password.resend-wait-minutes}")
        private long forgotPasswordResendWaitMinutes;

        // How long the one-time reset token stays valid after OTP verification
        private static final long RESET_TOKEN_TTL_MINUTES = 5;

        private String normalizeEmail(String email) {
                return email.trim().toLowerCase();
        }

        // Save OTP
        @Override
        public void saveOtp(String email, String otp) {
                email = normalizeEmail(email);
                redisTemplate.opsForValue().set(
                                OTP_PREFIX + email,
                                otp,
                                Duration.ofMinutes(forgotPasswordOtpExpiryMinutes));
        }

        // Get OTP
        @Override
        public String getOtp(String email) {
                email = normalizeEmail(email);
                return redisTemplate.opsForValue().get(OTP_PREFIX + email);
        }

        // Delete OTP
        @Override
        public void deleteOtp(String email) {
                email = normalizeEmail(email);
                redisTemplate.delete(OTP_PREFIX + email);
        }

        // Save resend cooldown
        @Override
        public void saveResendCooldown(String email) {
                email = normalizeEmail(email);
                redisTemplate.opsForValue().set(
                                RESEND_PREFIX + email,
                                "LOCKED",
                                Duration.ofMinutes(forgotPasswordResendWaitMinutes));
        }

        // Check resend cooldown
        @Override
        public boolean hasResendCooldown(String email) {
                email = normalizeEmail(email);
                return Boolean.TRUE.equals(redisTemplate.hasKey(RESEND_PREFIX + email));
        }

        // ── Reset token ──────────────────────────────────────────────────

        @Override
        public String issueResetToken(String email) {
                email = normalizeEmail(email);

                byte[] randomBytes = new byte[32]; // 256-bit, unguessable
                secureRandom.nextBytes(randomBytes);
                String token = Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(randomBytes);

                redisTemplate.opsForValue().set(
                                RESET_TOKEN_PREFIX + token,
                                email,
                                Duration.ofMinutes(RESET_TOKEN_TTL_MINUTES));

                return token;
        }

        @Override
        public String resolveResetToken(String token) {
                if (token == null || token.isBlank()) {
                        return null;
                }
                return redisTemplate.opsForValue().get(RESET_TOKEN_PREFIX + token);
        }

        @Override
        public void deleteResetToken(String token) {
                if (token == null || token.isBlank()) {
                        return;
                }
                redisTemplate.delete(RESET_TOKEN_PREFIX + token);
        }
}