package com.trackai.backend.service.impl;

import com.trackai.backend.entity.User;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.EmailVerificationService;
import com.trackai.backend.service.MailService;
import com.trackai.backend.service.RedisEmailVerificationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

        // SEND VERIFICATION EMAIL
        @Override
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
                String verificationLink =

                                frontendUrl
                                                +
                                                "/api/auth/verify?token="
                                                +
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
        public void verifyToken(
                        String token) {

                // GET EMAIL FROM REDIS
                String email =

                                redisVerificationTokenService

                                                .getEmailByToken(
                                                                token);

                // INVALID TOKEN
                if (email == null) {

                        throw new RuntimeException(

                                        "Invalid or expired verification token");
                }

                // FIND USER
                User user = userRepository

                                .findByEmail(email)

                                .orElseThrow(() ->

                                new RuntimeException(
                                                "User not found"));

                // ALREADY VERIFIED
                if (Boolean.TRUE.equals(
                                user.getEmailVerified())) {

                        throw new RuntimeException(
                                        "Email already verified");
                }

                // VERIFY USER
                user.setEnabled(true);

                user.setEmailVerified(true);

                // SAVE USER
                userRepository.save(user);

                // DELETE TOKEN
                redisVerificationTokenService

                                .deleteToken(token);
        }

        // RESEND VERIFICATION EMAIL
        @Override
        public void resendVerificationEmail(
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

                // ALREADY VERIFIED
                if (Boolean.TRUE.equals(
                                user.getEmailVerified())) {

                        throw new RuntimeException(
                                        "Email already verified");
                }

                // SEND NEW VERIFICATION EMAIL
                sendVerificationEmail(email);
        }
}