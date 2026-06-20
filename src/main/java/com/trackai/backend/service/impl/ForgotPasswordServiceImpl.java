package com.trackai.backend.service.impl;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.ForgotPasswordService;
import com.trackai.backend.service.MailService;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.RedisForgotPasswordOtpService;
import com.trackai.backend.service.RedisRefreshTokenService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@RequiredArgsConstructor
public class ForgotPasswordServiceImpl
                implements ForgotPasswordService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final MailService mailService;
        private final RedisForgotPasswordOtpService redisForgotPasswordOtpService;
        private final RedisRefreshTokenService redisRefreshTokenService;
        private final RedisRateLimitService redisRateLimitService;
        private final RateLimitProperties rateLimitProperties;

        @Value("${app.forgot-password.otp-expiry-minutes}")
        private long forgotPasswordOtpExpiryMinutes;

        @Value("${app.forgot-password.resend-wait-minutes}")
        private long forgotPasswordResendWaitMinutes;

        // Normalize email
        private String normalizeEmail(String email) {

                return email.trim().toLowerCase();
        }

        // Generate OTP
        private String generateOtp() {

                return String.valueOf(
                                100000 + new Random().nextInt(900000));
        }

        // Send forgot password OTP
        @Override
        public void sendForgotPasswordOtp(String email) {

                // Rate limit
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "forgot-password:" + email,

                                rateLimitProperties
                                                .getForgotPassword()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getForgotPassword()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getForgotPassword()
                                                .getRefillMinutes());

                // Block request
                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                // Normalize email
                email = normalizeEmail(email);

                // Find user
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found"));

                // Admin check
                if (user.getRole().name().equals("ROLE_ADMIN")) {

                        throw new RuntimeException(
                                        "Admin password reset is not allowed");
                }

                // Blocked user
                if (Boolean.TRUE.equals(user.getBlocked())) {

                        throw new RuntimeException(
                                        "Your account is blocked");
                }

                // Disabled user
                if (!Boolean.TRUE.equals(user.getEnabled())) {

                        throw new RuntimeException(
                                        "Your account is disabled");
                }

                // Email verification
                if (!Boolean.TRUE.equals(user.getEmailVerified())) {

                        throw new RuntimeException(
                                        "Please verify your email first");
                }

                // Resend cooldown
                if (redisForgotPasswordOtpService
                                .hasResendCooldown(email)) {

                        throw new RuntimeException(
                                        "Please wait "
                                                        + forgotPasswordResendWaitMinutes
                                                        + " minutes before requesting another OTP");
                }

                // Generate OTP
                String otp = generateOtp();

                // Save OTP
                redisForgotPasswordOtpService.saveOtp(
                                email,
                                otp);

                // Save resend cooldown
                redisForgotPasswordOtpService
                                .saveResendCooldown(email);

                // Send email
                mailService.sendForgotPasswordOtp(
                                user.getName(),
                                user.getEmail(),
                                otp,
                                forgotPasswordOtpExpiryMinutes);

                System.out.println(
                                "FORGOT PASSWORD OTP SENT : "
                                                + email);
        }

        // Verify forgot password OTP
        @Override
        public void verifyForgotPasswordOtp(
                        String email,
                        String otp) {

                // Rate limit
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "verify-forgot-password-otp:" + email,

                                rateLimitProperties
                                                .getVerifyForgotPasswordOtp()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getVerifyForgotPasswordOtp()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getVerifyForgotPasswordOtp()
                                                .getRefillMinutes());

                // Block request
                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                // Normalize email
                email = normalizeEmail(email);

                // Find user
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found"));

                // Get stored OTP
                String storedOtp = redisForgotPasswordOtpService
                                .getOtp(email);

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

                // Save verified state
                redisForgotPasswordOtpService
                                .saveVerifiedState(email);

                System.out.println(
                                "FORGOT PASSWORD OTP VERIFIED : "
                                                + email);
        }

        // Reset password
        @Override
        public void resetPassword(
                        String email,
                        String newPassword) {

                // Rate limit
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "reset-password:" + email,

                                rateLimitProperties
                                                .getResetPassword()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getResetPassword()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getResetPassword()
                                                .getRefillMinutes());

                // Block request
                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                // Normalize email
                email = normalizeEmail(email);

                // Find user
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found"));

                // OTP verification required
                if (!redisForgotPasswordOtpService
                                .isOtpVerified(email)) {

                        throw new RuntimeException(
                                        "OTP verification required");
                }

                // Same password validation
                if (passwordEncoder.matches(
                                newPassword,
                                user.getPassword())) {

                        throw new RuntimeException(
                                        "New password cannot be same as old password");
                }

                // Update password
                user.setPassword(
                                passwordEncoder.encode(newPassword));

                // Save user
                userRepository.save(user);

                // Delete OTP
                redisForgotPasswordOtpService
                                .deleteOtp(email);

                // Delete verified state
                redisForgotPasswordOtpService
                                .deleteVerifiedState(email);

                // Logout all devices
                redisRefreshTokenService
                                .deleteAllRefreshTokens(email);

                System.out.println(
                                "PASSWORD RESET SUCCESSFUL : "
                                                + email);

                System.out.println(
                                "ALL DEVICES LOGGED OUT : "
                                                + email);
        }

        // Resend forgot password OTP
        @Override
        public void resendForgotPasswordOtp(String email) {

                // Rate limit
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "resend-forgot-password-otp:" + email,

                                rateLimitProperties
                                                .getResendForgotPasswordOtp()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getResendForgotPasswordOtp()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getResendForgotPasswordOtp()
                                                .getRefillMinutes());

                // Block request
                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                // Normalize email
                email = normalizeEmail(email);

                // Resend cooldown
                if (redisForgotPasswordOtpService
                                .hasResendCooldown(email)) {

                        throw new RuntimeException(
                                        "Resend available after "
                                                        + forgotPasswordResendWaitMinutes
                                                        + " minutes");
                }

                // Send OTP
                sendForgotPasswordOtp(email);

                System.out.println(
                                "FORGOT PASSWORD OTP RESENT : "
                                                + email);
        }
}