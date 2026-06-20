package com.trackai.backend.service.impl;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.exception.AccountBlockedException;
import com.trackai.backend.exception.AccountDisabledException;
import com.trackai.backend.exception.InvalidCredentialsException;
import com.trackai.backend.exception.OtpException;
import com.trackai.backend.exception.RateLimitExceededException;
import com.trackai.backend.exception.ResourceNotFoundException;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.ForgotPasswordService;
import com.trackai.backend.service.MailService;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.RedisForgotPasswordOtpService;
import com.trackai.backend.service.RedisRefreshTokenService;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class ForgotPasswordServiceImpl
                implements ForgotPasswordService {

        private static final Logger log = LoggerFactory.getLogger(ForgotPasswordServiceImpl.class);

        private static final SecureRandom secureRandom = new SecureRandom();

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
                                100000 + secureRandom.nextInt(900000));
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

                        throw new RateLimitExceededException(
                                        rateLimitResponse.getMessage());
                }

                // Normalize email
                email = normalizeEmail(email);

                // Find user
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found"));

                // Admin check
                if (user.getRole().name().equals("ROLE_ADMIN")) {

                        throw new InvalidCredentialsException(
                                        "Admin password reset is not allowed");
                }

                // Blocked user
                if (Boolean.TRUE.equals(user.getBlocked())) {

                        throw new AccountBlockedException(
                                        "Your account is blocked");
                }

                // Disabled user
                if (!Boolean.TRUE.equals(user.getEnabled())) {

                        throw new AccountDisabledException(
                                        "Your account is disabled");
                }

                // Email verification
                if (!Boolean.TRUE.equals(user.getEmailVerified())) {

                        throw new AccountDisabledException(
                                        "Please verify your email first");
                }

                // Resend cooldown
                if (redisForgotPasswordOtpService
                                .hasResendCooldown(email)) {

                        throw new RateLimitExceededException(
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

                log.info("Forgot password OTP sent for email: {}", email);
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

                        throw new RateLimitExceededException(
                                        rateLimitResponse.getMessage());
                }

                // Normalize email
                email = normalizeEmail(email);

                // Find user
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found"));

                // Get stored OTP
                String storedOtp = redisForgotPasswordOtpService
                                .getOtp(email);

                // OTP expired
                if (storedOtp == null) {

                        throw new OtpException(
                                        "OTP expired or not found");
                }

                // Invalid OTP
                if (!storedOtp.equals(otp)) {

                        throw new OtpException(
                                        "Invalid OTP");
                }

                // Save verified state
                redisForgotPasswordOtpService
                                .saveVerifiedState(email);

                log.info("Forgot password OTP verified for email: {}", email);
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

                        throw new RateLimitExceededException(
                                        rateLimitResponse.getMessage());
                }

                // Normalize email
                email = normalizeEmail(email);

                // Find user
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found"));

                // OTP verification required
                if (!redisForgotPasswordOtpService
                                .isOtpVerified(email)) {

                        throw new OtpException(
                                        "OTP verification required");
                }

                // Same password validation
                if (passwordEncoder.matches(
                                newPassword,
                                user.getPassword())) {

                        throw new InvalidCredentialsException(
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

                log.info("Password reset successful for email: {}", email);

                log.info("All devices logged out for email: {}", email);
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

                        throw new RateLimitExceededException(
                                        rateLimitResponse.getMessage());
                }

                // Normalize email
                email = normalizeEmail(email);

                // Resend cooldown
                if (redisForgotPasswordOtpService
                                .hasResendCooldown(email)) {

                        throw new RateLimitExceededException(
                                        "Resend available after "
                                                        + forgotPasswordResendWaitMinutes
                                                        + " minutes");
                }

                // Send OTP
                sendForgotPasswordOtp(email);

                log.info("Forgot password OTP resent for email: {}", email);
        }
}