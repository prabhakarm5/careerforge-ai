package com.trackai.backend.service.impl;

import com.trackai.backend.service.RedisAdminOtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisAdminOtpServiceImpl implements RedisAdminOtpService {
    private static final String OTP_PREFIX = "admin_login_otp:";
    private static final String RESEND_PREFIX = "admin_login_resend:";
    private static final String REVEAL_PREFIX = "admin_login_otp_reveal:";
    private static final String REVEAL_EMAIL_PREFIX = "admin_login_otp_reveal_email:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.otp.expiry-minutes}")
    private long otpExpiryMinutes;

    @Value("${app.otp.resend-wait-minutes}")
    private long resendWaitMinutes;

    @Override
    public void saveOtp(String email, String otp) {
        redisTemplate.opsForValue().set(otpKey(email), otp, Duration.ofMinutes(otpExpiryMinutes));
    }

    @Override
    public String getOtp(String email) {
        return redisTemplate.opsForValue().get(otpKey(email));
    }

    @Override
    public void deleteOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        String reverseKey = REVEAL_EMAIL_PREFIX + normalizedEmail;
        String revealToken = redisTemplate.opsForValue().get(reverseKey);
        List<String> keys = new ArrayList<>(List.of(OTP_PREFIX + normalizedEmail, reverseKey));
        if (revealToken != null && !revealToken.isBlank()) keys.add(REVEAL_PREFIX + revealToken);
        redisTemplate.delete(keys);
    }

    @Override
    public void saveResendCooldown(String email) {
        redisTemplate.opsForValue().set(
                RESEND_PREFIX + normalizeEmail(email),
                "LOCKED",
                Duration.ofMinutes(resendWaitMinutes));
    }

    @Override
    public boolean hasResendCooldown(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RESEND_PREFIX + normalizeEmail(email)));
    }

    @Override
    public void saveRevealToken(String email, String token) {
        String normalizedEmail = normalizeEmail(email);
        String reverseKey = REVEAL_EMAIL_PREFIX + normalizedEmail;
        String oldToken = redisTemplate.opsForValue().get(reverseKey);
        if (oldToken != null) redisTemplate.delete(REVEAL_PREFIX + oldToken);

        Duration ttl = Duration.ofMinutes(otpExpiryMinutes);
        redisTemplate.opsForValue().set(REVEAL_PREFIX + token, normalizedEmail, ttl);
        redisTemplate.opsForValue().set(reverseKey, token, ttl);
    }

    @Override
    public String getEmailByRevealToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        return redisTemplate.opsForValue().get(REVEAL_PREFIX + token);
    }

    @Override
    public long getOtpTtlSeconds(String email) {
        Long ttl = redisTemplate.getExpire(otpKey(email), TimeUnit.SECONDS);
        return ttl == null ? -1 : ttl;
    }

    private String otpKey(String email) {
        return OTP_PREFIX + normalizeEmail(email);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}