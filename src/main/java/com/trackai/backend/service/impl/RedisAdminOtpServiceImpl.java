package com.trackai.backend.service.impl;

import com.trackai.backend.service.RedisAdminOtpService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisAdminOtpServiceImpl

                implements RedisAdminOtpService {

        private final StringRedisTemplate redisTemplate;

        // ADMIN OTP EXPIRY
        @Value("${app.otp.expiry-minutes}")
        private long otpExpiryMinutes;

        // ADMIN RESEND WAIT
        @Value("${app.otp.resend-wait-minutes}")
        private long resendWaitMinutes;

        private static final String OTP_PREFIX =

                        "admin_login_otp:";

        private static final String RESEND_PREFIX =

                        "admin_login_resend:";

        // NORMALIZE EMAIL
        private String normalizeEmail(
                        String email) {

                return email

                                .trim()

                                .toLowerCase();
        }

        // SAVE OTP
        @Override
        public void saveOtp(

                        String email,

                        String otp) {

                // NORMALIZE EMAIL
                email = normalizeEmail(email);

                // SAVE OTP
                redisTemplate.opsForValue().set(OTP_PREFIX + email, otp, Duration.ofMinutes(otpExpiryMinutes));
        }

        // GET OTP
        @Override
        public String getOtp(
                        String email) {

                // NORMALIZE EMAIL
                email = normalizeEmail(email);

                return redisTemplate.opsForValue()

                                .get(OTP_PREFIX + email);
        }

        // DELETE OTP
        @Override
        public void deleteOtp(
                        String email) {

                // NORMALIZE EMAIL
                email = normalizeEmail(email);

                redisTemplate.delete(
                                OTP_PREFIX + email);
        }

        // SAVE RESEND COOLDOWN
        @Override
        public void saveResendCooldown(
                        String email) {

                // NORMALIZE EMAIL
                email = normalizeEmail(email);

                redisTemplate.opsForValue()

                                .set(

                                                RESEND_PREFIX + email,

                                                "LOCKED",

                                                Duration.ofMinutes(
                                                                resendWaitMinutes));
        }

        // CHECK RESEND COOLDOWN
        @Override
        public boolean hasResendCooldown(
                        String email) {

                // NORMALIZE EMAIL
                email = normalizeEmail(email);

                return Boolean.TRUE.equals(

                                redisTemplate.hasKey(
                                                RESEND_PREFIX + email));
        }
}