package com.trackai.backend.service.impl;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.AdminOtpRevealResponse;
import com.trackai.backend.dto.LoginResponse;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.Role;
import com.trackai.backend.exception.AccountBlockedException;
import com.trackai.backend.exception.AccountDisabledException;
import com.trackai.backend.exception.InvalidCredentialsException;
import com.trackai.backend.exception.OtpException;
import com.trackai.backend.exception.RateLimitExceededException;
import com.trackai.backend.exception.ResourceNotFoundException;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.security.JwtUtil;
import com.trackai.backend.service.AdminOtpLoginService;
import com.trackai.backend.service.MailService;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.RedisAdminOtpService;
import com.trackai.backend.service.RedisRefreshTokenService;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AdminOtpLoginServiceImpl implements AdminOtpLoginService {

        private static final Logger log = LoggerFactory.getLogger(AdminOtpLoginServiceImpl.class);

        private static final SecureRandom secureRandom = new SecureRandom();

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
                                100000 + secureRandom.nextInt(900000));
        }

        private String generateRevealToken() {
                byte[] bytes = new byte[32];
                secureRandom.nextBytes(bytes);
                return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

                        throw new RateLimitExceededException(
                                        rateLimitResponse.getMessage());
                }

                email = normalizeEmail(email);

                // Find user
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new InvalidCredentialsException(
                                                "Invalid email or password"));

                // Admin validation
                if (user.getRole() != Role.ROLE_ADMIN) {

                        throw new InvalidCredentialsException(
                                        "Access denied. Admin account required");
                }

                // Password validation
                if (!passwordEncoder.matches(
                                password,
                                user.getPassword())) {

                        throw new InvalidCredentialsException(
                                        "Invalid email or password");
                }

                // Account checks
                if (Boolean.TRUE.equals(user.getBlocked())) {

                        throw new AccountBlockedException(
                                        "Admin account is blocked");
                }

                if (!Boolean.TRUE.equals(user.getEnabled())) {

                        throw new AccountDisabledException(
                                        "Admin account is disabled");
                }

                if (!Boolean.TRUE.equals(user.getEmailVerified())) {

                        throw new AccountDisabledException(
                                        "Please verify your email first");
                }

                // Resend cooldown
                if (redisAdminOtpService.hasResendCooldown(email)) {

                        throw new RateLimitExceededException(
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

                String revealToken = generateRevealToken();
                redisAdminOtpService.saveRevealToken(email, revealToken);

                // Save resend cooldown
                redisAdminOtpService.saveResendCooldown(email);

                // ✅ FIX — pehle ye synchronous tha (mailService.sendAdminLoginOtp(...)
                // seedha yahin call ho raha tha), jiski wajah se SMTP handshake/send
                // poora hone tak HTTP response hi client ko nahi jaata tha —
                // isi se 50-70 second ka delay aa raha tha OTP bhejte waqt.
                //
                // Ab email async thread pool (AsyncConfig ka default executor) mein
                // background me bheji jaayegi. OTP already Redis me save ho chuka
                // hai is point tak, isliye response turant client ko chala jaayega
                // — email thodi der baad silently deliver hogi.
                mailService.sendAdminLoginOtp(
                                user.getName(),
                                user.getEmail(),
                                revealToken,
                                otpExpiryMinutes);

                log.info("Admin login OTP queued for email: {}", email);
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

                        throw new RateLimitExceededException(
                                        rateLimitResponse.getMessage());
                }

                email = normalizeEmail(email);
                fingerprint = normalizeFingerprint(fingerprint);

                // Find user
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found"));

                // Get stored OTP
                String storedOtp = redisAdminOtpService.getOtp(email);

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

                // Absolute session expiry
                long absoluteExpiry = System.currentTimeMillis()
                                + adminAbsoluteSessionExpiry.toMillis();

                // Generate tokens
                String accessToken = jwtUtil.generateAdminAccessToken(
                                user.getEmail(),
                                user.getId(),
                                fingerprint,
                                absoluteExpiry);

                String refreshToken = jwtUtil.generateAdminRefreshToken(
                                user.getEmail(),
                                user.getId(),
                                fingerprint,
                                absoluteExpiry);

                // Save refresh token
                redisRefreshTokenService.saveRefreshToken(
                                user.getEmail(),
                                fingerprint,
                                refreshToken);

                // Delete OTP
                redisAdminOtpService.deleteOtp(email);

                log.info("Admin login OTP verified for email: {}", email);

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

                        throw new RateLimitExceededException(
                                        rateLimitResponse.getMessage());
                }

                email = normalizeEmail(email);

                // Resend cooldown
                if (redisAdminOtpService.hasResendCooldown(email)) {

                        throw new RateLimitExceededException(
                                        "Resend available after "
                                                        + resendWaitMinutes
                                                        + " minutes");
                }

                // Find user
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found"));

                // Generate OTP
                String otp = generateOtp();

                // Save OTP
                redisAdminOtpService.saveOtp(
                                email,
                                otp);

                String revealToken = generateRevealToken();
                redisAdminOtpService.saveRevealToken(email, revealToken);

                // Save resend cooldown
                redisAdminOtpService.saveResendCooldown(email);

                // ✅ FIX — same reason as sendAdminLoginOtp() — async email
                mailService.sendAdminLoginOtp(
                                user.getName(),
                                user.getEmail(),
                                revealToken,
                                otpExpiryMinutes);

                log.info("Admin login OTP resend queued for email: {}", email);
        }

        @Override
        public AdminOtpRevealResponse revealAdminLoginOtp(String token) {
                String email = redisAdminOtpService.getEmailByRevealToken(token);
                if (email == null) {
                        throw new ResourceNotFoundException("Admin OTP link is invalid or expired");
                }

                String otp = redisAdminOtpService.getOtp(email);
                long expiresInSeconds = redisAdminOtpService.getOtpTtlSeconds(email);
                if (otp == null || expiresInSeconds <= 0) {
                        throw new ResourceNotFoundException("Admin OTP link is invalid or expired");
                }
                return new AdminOtpRevealResponse(otp, expiresInSeconds);
        }}