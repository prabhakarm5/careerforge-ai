package com.trackai.backend.service.impl;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.exception.RateLImitException;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.EmailVerificationService;
import com.trackai.backend.service.MailService;
import com.trackai.backend.service.RedisEmailVerificationTokenService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl

                implements EmailVerificationService {

        @Value("${app.frontend-url}")
        private String frontendUrl;

        @Value("${app.mail.verification-expiry-minutes}")
        private long verificationExpiryMinutes;

        @Value("${app.mail.resend-wait-minutes}")
        private long resendWaitMinutes;

        private final UserRepository userRepository;

        private final MailService mailService;

        private final RedisEmailVerificationTokenService redisVerificationTokenService;

        private final RedisRateLimitServiceImpl redisRateLimitService;

        private final RateLimitProperties rateLimitProperties;

        private static Logger logger = LoggerFactory.getLogger(EmailVerificationServiceImpl.class);

        // SEND VERIFICATION EMAIL
        @Override
        @Async
        public void sendVerificationEmail(
                        String email) {

                // NORMALIZE EMAIL
                email = email

                                .trim()

                                .toLowerCase();

                // FIND USER
                User user = userRepository

                                .findByEmail(email)

                                .orElseThrow(() ->

                                new RuntimeException(
                                                "User not found"));

                // GENERATE TOKEN
                String token =

                                UUID.randomUUID()
                                                .toString();

                // SAVE TOKEN IN REDIS
                redisVerificationTokenService

                                .saveVerificationToken(

                                                email,

                                                token);

                // CREATE VERIFY LINK
                String verificationLink = frontendUrl +
                                "/verify-email?token=" +
                                token;

                // SEND EMAIL
                mailService.sendVerificationEmail(

                                user.getName(),

                                user.getEmail(),

                                verificationLink,

                                verificationExpiryMinutes);
        }

        // VERIFY TOKEN
        @Override
        public void verifyToken(String token) {

                // Rate limit
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "VerifyToken:" + token,

                                rateLimitProperties
                                                .getVerifyToken()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getVerifyToken()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getVerifyToken()
                                                .getRefillMinutes());

                // Block request
                if (!rateLimitResponse.isAllowed()) {

                        throw new RateLImitException(
                                        rateLimitResponse.getMessage());
                }

                logger.info("TOKEN RECEIVED = {}", token);

                String email = redisVerificationTokenService
                                .getEmailByToken(token);

                logger.info("EMAIL FROM REDIS = {}", email);

                if (email == null) {

                        throw new RuntimeException(
                                        "Invalid or expired verification token");
                }

                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found"));

                // Already verified
                if (Boolean.TRUE.equals(
                                user.getEmailVerified())) {

                        throw new RuntimeException(
                                        "Email already verified");
                }

                // Verify user
                user.setEnabled(true);
                user.setEmailVerified(true);

                // SAVE USER
                userRepository.save(user);

                // DELETE TOKEN
                redisVerificationTokenService
                                .deleteToken(token);

                logger.info(
                                "EMAIL VERIFIED SUCCESSFULLY = {}", email);
        }

        // RESEND VERIFICATION EMAIL
        @Override
        public void resendVerificationEmail(
                        String email) {

                // Rate limit
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "ResendVerificationEmail:" + email,

                                rateLimitProperties
                                                .getResendVerificationEmail()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getResendVerificationEmail()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getResendVerificationEmail()
                                                .getRefillMinutes());

                // Block request
                if (!rateLimitResponse.isAllowed()) {

                        throw new RateLImitException(
                                        rateLimitResponse.getMessage());
                }
                email = email
                                .trim()
                                .toLowerCase();

                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found"));

                if (Boolean.TRUE.equals(
                                user.getEmailVerified())) {

                        throw new RuntimeException(
                                        "Email already verified");
                }

                if (redisVerificationTokenService
                                .hasResendCooldown(email)) {

                        Long remainingSeconds =

                                        redisVerificationTokenService
                                                        .getResendCooldownSeconds(
                                                                        email);

                        throw new RuntimeException(

                                        "Please wait "

                                                        + remainingSeconds

                                                        + " seconds before requesting another verification email"

                        );
                }

                redisVerificationTokenService
                                .saveResendCooldown(email);

                sendVerificationEmail(email);
        }
}