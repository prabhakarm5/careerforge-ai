package com.trackai.backend.service;

public interface EmailVerificationService {

        // SEND VERIFICATION EMAIL
        void sendVerificationEmail(
                        String email);

        // VERIFY EMAIL TOKEN
        void verifyToken(
                        String token);

        // RESEND VERIFICATION EMAIL
        void resendVerificationEmail(
                        String email);
}