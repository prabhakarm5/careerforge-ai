package com.trackai.backend.service.impl;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.LoginResponse;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.Role;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.security.JwtUtil;
import com.trackai.backend.service.AdminOtpLoginService;
import com.trackai.backend.service.MailService;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.RedisAdminOtpService;
import com.trackai.backend.service.RedisRefreshTokenService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AdminOtpLoginServiceImpl implements AdminOtpLoginService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtUtil jwtUtil;
        private final MailService mailService;
        private final RedisAdminOtpService redisAdminOtpService;
        private final RedisRefreshTokenService redisRefreshTokenService;
        private final RedisRateLimitService redisRateLimitService;
        private final RateLimitProperties rateLimitProperties;

        @Value("${app.otp.expiry-minutes}")
        private long otpExpiryMinutes;

        @Value("${app.otp.resend-wait-minutes}")
        private long resendWaitMinutes;

        @Value("${app.admin.absolute-session-expiry}")
        private Duration adminAbsoluteSessionExpiry;

        // Normalize email
        private String normalizeEmail(String email) {

                return email.trim().toLowerCase();
        }

        // Normalize fingerprint
        private String normalizeFingerprint(String fingerprint) {

                return fingerprint.trim().toLowerCase();
        }

        // Generate 6-digit OTP
        private String generateOtp() {

                return String.valueOf(
                                100000 + new Random().nextInt(900000));
        }

        // Send admin login OTP
        @Override
        public void sendAdminLoginOtp(
                        String email,
                        String password) {

                // Rate limit
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "admin-login:" + email,

                                rateLimitProperties
                                                .getAdminLogin()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getAdminLogin()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getAdminLogin()
                                                .getRefillMinutes());

                // Block request
                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                email = normalizeEmail(email);

                // Find user
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "Invalid email or password"));

                // Admin validation
                if (user.getRole() != Role.ROLE_ADMIN) {

                        throw new RuntimeException(
                                        "Access denied. Admin account required");
                }

                // Password validation
                if (!passwordEncoder.matches(
                                password,
                                user.getPassword())) {

                        throw new RuntimeException(
                                        "Invalid email or password");
                }

                // Account checks
                if (Boolean.TRUE.equals(user.getBlocked())) {

                        throw new RuntimeException(
                                        "Admin account is blocked");
                }

                if (!Boolean.TRUE.equals(user.getEnabled())) {

                        throw new RuntimeException(
                                        "Admin account is disabled");
                }

                if (!Boolean.TRUE.equals(user.getEmailVerified())) {

                        throw new RuntimeException(
                                        "Please verify your email first");
                }

                // Resend cooldown
                if (redisAdminOtpService.hasResendCooldown(email)) {

                        throw new RuntimeException(
                                        "Please wait "
                                                        + resendWaitMinutes
                                                        + " minutes before requesting another OTP");
                }

                // Generate OTP
                String otp = generateOtp();

                // Save OTP
                redisAdminOtpService.saveOtp(
                                email,
                                otp);

                // Save resend cooldown
                redisAdminOtpService.saveResendCooldown(email);

                // Send email
                mailService.sendAdminLoginOtp(
                                user.getName(),
                                user.getEmail(),
                                otp,
                                otpExpiryMinutes);
        }

        // Verify admin login OTP
        @Override
        public LoginResponse verifyAdminLoginOtp(
                        String email,
                        String otp,
                        String fingerprint) {

                // Rate limit
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "verify-admin-otp:" + email,

                                rateLimitProperties
                                                .getVerifyAdminOtp()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getVerifyAdminOtp()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getVerifyAdminOtp()
                                                .getRefillMinutes());

                // Block request
                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                email = normalizeEmail(email);
                fingerprint = normalizeFingerprint(fingerprint);

                // Find user
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found"));

                // Get stored OTP
                String storedOtp = redisAdminOtpService.getOtp(email);

                // OTP expired
                if (storedOtp == null) {

                        throw new RuntimeException(
                                        "OTP expired or not found");
                }

                // Invalid OTP
                if (!storedOtp.equals(otp)) {

                        throw new RuntimeException(
                                        "Invalid OTP");
                }

                // Absolute session expiry
                long absoluteExpiry = System.currentTimeMillis()
                                + adminAbsoluteSessionExpiry.toMillis();

                // Generate tokens
                String accessToken = jwtUtil.generateAdminAccessToken(
                                user.getEmail(),
                                fingerprint);

                String refreshToken = jwtUtil.generateAdminRefreshToken(
                                user.getEmail(),
                                fingerprint,
                                absoluteExpiry);

                // Save refresh token
                redisRefreshTokenService.saveRefreshToken(
                                user.getEmail(),
                                fingerprint,
                                refreshToken);

                // Delete OTP
                redisAdminOtpService.deleteOtp(email);

                // Response
                return LoginResponse.builder()
                                .id(user.getId())
                                .name(user.getName())
                                .email(user.getEmail())
                                .role(user.getRole().name())
                                .accessToken(accessToken)
                                .refreshToken(refreshToken)
                                .fingerprint(fingerprint)
                                .build();
        }

        // Resend admin login OTP
        @Override
        public void resendAdminLoginOtp(String email) {

                // Rate limit
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "resend-admin-otp:" + email,

                                rateLimitProperties
                                                .getResendAdminOtp()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getResendAdminOtp()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getResendAdminOtp()
                                                .getRefillMinutes());

                // Block request
                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                email = normalizeEmail(email);

                // Resend cooldown
                if (redisAdminOtpService.hasResendCooldown(email)) {

                        throw new RuntimeException(
                                        "Resend available after "
                                                        + resendWaitMinutes
                                                        + " minutes");
                }

                // Find user
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found"));

                // Generate OTP
                String otp = generateOtp();

                // Save OTP
                redisAdminOtpService.saveOtp(
                                email,
                                otp);

                // Save resend cooldown
                redisAdminOtpService.saveResendCooldown(email);

                // Send email
                mailService.sendAdminLoginOtp(
                                user.getName(),
                                user.getEmail(),
                                otp,
                                otpExpiryMinutes);
        }
}