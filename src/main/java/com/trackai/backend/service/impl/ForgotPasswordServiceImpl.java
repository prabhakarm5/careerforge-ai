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
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ForgotPasswordServiceImpl implements ForgotPasswordService {

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

        private String normalizeEmail(String email) {
                return email.trim().toLowerCase();
        }

        private String generateOtp() {
                return String.valueOf(100000 + secureRandom.nextInt(900000));
        }

        @Override
        public void sendForgotPasswordOtp(String email) {

                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(
                                "forgot-password:" + email,
                                rateLimitProperties.getForgotPassword().getCapacity(),
                                rateLimitProperties.getForgotPassword().getRefillTokens(),
                                rateLimitProperties.getForgotPassword().getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {
                        throw new RateLimitExceededException(rateLimitResponse.getMessage());
                }

                email = normalizeEmail(email);

                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                if (user.getRole().name().equals("ROLE_ADMIN")) {
                        throw new InvalidCredentialsException("Admin password reset is not allowed");
                }
                if (Boolean.TRUE.equals(user.getBlocked())) {
                        throw new AccountBlockedException("Your account is blocked");
                }
                if (!Boolean.TRUE.equals(user.getEnabled())) {
                        throw new AccountDisabledException("Your account is disabled");
                }
                if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                        throw new AccountDisabledException("Please verify your email first");
                }
                if (redisForgotPasswordOtpService.hasResendCooldown(email)) {
                        throw new RateLimitExceededException(
                                        "Please wait " + forgotPasswordResendWaitMinutes
                                                        + " minutes before requesting another OTP");
                }

                String otp = generateOtp();
                redisForgotPasswordOtpService.saveOtp(email, otp);
                redisForgotPasswordOtpService.saveResendCooldown(email);

                mailService.sendForgotPasswordOtp(
                                user.getName(), user.getEmail(), otp, forgotPasswordOtpExpiryMinutes);

                log.info("Forgot password OTP sent for email: {}", email);
        }

        // ── VERIFY OTP — single use, returns a one-time reset token ────────
        @Override
        public String verifyForgotPasswordOtp(String email, String otp) {

                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(
                                "verify-forgot-password-otp:" + email,
                                rateLimitProperties.getVerifyForgotPasswordOtp().getCapacity(),
                                rateLimitProperties.getVerifyForgotPasswordOtp().getRefillTokens(),
                                rateLimitProperties.getVerifyForgotPasswordOtp().getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {
                        throw new RateLimitExceededException(rateLimitResponse.getMessage());
                }

                email = normalizeEmail(email);

                userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                String storedOtp = redisForgotPasswordOtpService.getOtp(email);

                if (storedOtp == null) {
                        throw new OtpException("OTP expired or not found");
                }
                if (!storedOtp.equals(otp)) {
                        throw new OtpException("Invalid OTP");
                }

                // ── OTP is single-use: kill it immediately so it can NEVER be
                // replayed, even within its original expiry window.
                redisForgotPasswordOtpService.deleteOtp(email);

                // ── Issue a fresh unguessable reset token bound to this email.
                // Frontend must send THIS token (not the email) to reset-password.
                String resetToken = redisForgotPasswordOtpService.issueResetToken(email);

                log.info("Forgot password OTP verified for email: {}", email);

                return resetToken;
        }

        // ── RESET PASSWORD — requires a valid, unused reset token ──────────
        @Override
        public void resetPassword(String resetToken, String newPassword) {

                String email = redisForgotPasswordOtpService.resolveResetToken(resetToken);

                if (email == null) {
                        throw new OtpException("Reset link expired or invalid. Please verify OTP again.");
                }

                email = normalizeEmail(email);

                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(
                                "reset-password:" + email,
                                rateLimitProperties.getResetPassword().getCapacity(),
                                rateLimitProperties.getResetPassword().getRefillTokens(),
                                rateLimitProperties.getResetPassword().getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {
                        throw new RateLimitExceededException(rateLimitResponse.getMessage());
                }

                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                if (passwordEncoder.matches(newPassword, user.getPassword())) {
                        throw new InvalidCredentialsException("New password cannot be same as old password");
                }

                user.setPassword(passwordEncoder.encode(newPassword));
                user.setPasswordChangedAt(LocalDateTime.now());
                userRepository.save(user);

                // Token is single use — kill it the moment it's consumed.
                redisForgotPasswordOtpService.deleteResetToken(resetToken);

                redisRefreshTokenService.deleteAllRefreshTokens(email);

                log.info("Password reset successful for email: {}", email);
                log.info("All devices logged out for email: {}", email);
        }

        @Override
        public void resendForgotPasswordOtp(String email) {

                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(
                                "resend-forgot-password-otp:" + email,
                                rateLimitProperties.getResendForgotPasswordOtp().getCapacity(),
                                rateLimitProperties.getResendForgotPasswordOtp().getRefillTokens(),
                                rateLimitProperties.getResendForgotPasswordOtp().getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {
                        throw new RateLimitExceededException(rateLimitResponse.getMessage());
                }

                email = normalizeEmail(email);

                if (redisForgotPasswordOtpService.hasResendCooldown(email)) {
                        throw new RateLimitExceededException(
                                        "Resend available after " + forgotPasswordResendWaitMinutes + " minutes");
                }

                sendForgotPasswordOtp(email);

                log.info("Forgot password OTP resent for email: {}", email);
        }
}