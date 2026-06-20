package com.trackai.backend.service.impl;

import com.trackai.backend.service.RedisForgotPasswordOtpService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisForgotPasswordOtpServiceImpl
                implements RedisForgotPasswordOtpService {

        private static final String OTP_PREFIX = "forgot_password_otp:";

        private static final String VERIFIED_PREFIX = "forgot_password_verified:";

        private static final String RESEND_PREFIX = "forgot_password_resend:";

        private final StringRedisTemplate redisTemplate;

        @Value("${app.forgot-password.otp-expiry-minutes}")
        private long forgotPasswordOtpExpiryMinutes;

        @Value("${app.forgot-password.resend-wait-minutes}")
        private long forgotPasswordResendWaitMinutes;

        // Normalize email
        private String normalizeEmail(String email) {

                return email.trim().toLowerCase();
        }

        // Save OTP
        @Override
        public void saveOtp(
                        String email,
                        String otp) {

                email = normalizeEmail(email);

                redisTemplate.opsForValue().set(
                                OTP_PREFIX + email,
                                otp,
                                Duration.ofMinutes(
                                                forgotPasswordOtpExpiryMinutes));
        }

        // Get OTP
        @Override
        public String getOtp(String email) {

                email = normalizeEmail(email);

                return redisTemplate.opsForValue()
                                .get(OTP_PREFIX + email);
        }

        // Delete OTP
        @Override
        public void deleteOtp(String email) {

                email = normalizeEmail(email);

                redisTemplate.delete(
                                OTP_PREFIX + email);
        }

        // Save verified state
        @Override
        public void saveVerifiedState(String email) {

                email = normalizeEmail(email);

                redisTemplate.opsForValue().set(
                                VERIFIED_PREFIX + email,
                                "VERIFIED",
                                Duration.ofMinutes(5));
        }

        // Check verified state
        @Override
        public boolean isOtpVerified(String email) {

                email = normalizeEmail(email);

                return Boolean.TRUE.equals(
                                redisTemplate.hasKey(
                                                VERIFIED_PREFIX + email));
        }

        // Delete verified state
        @Override
        public void deleteVerifiedState(String email) {

                email = normalizeEmail(email);

                redisTemplate.delete(
                                VERIFIED_PREFIX + email);
        }

        // Save resend cooldown
        @Override
        public void saveResendCooldown(String email) {

                email = normalizeEmail(email);

                redisTemplate.opsForValue().set(
                                RESEND_PREFIX + email,
                                "LOCKED",
                                Duration.ofMinutes(
                                                forgotPasswordResendWaitMinutes));
        }

        // Check resend cooldown
        @Override
        public boolean hasResendCooldown(String email) {

                email = normalizeEmail(email);

                return Boolean.TRUE.equals(
                                redisTemplate.hasKey(
                                                RESEND_PREFIX + email));
        }
}