package com.trackai.backend.service;

public interface RedisForgotPasswordOtpService {

        // SAVE OTP
        void saveOtp(String email, String otp);

        // GET OTP
        String getOtp(String email);

        // DELETE OTP
        void deleteOtp(String email);

        // SAVE RESEND COOLDOWN
        void saveResendCooldown(String email);

        // CHECK RESEND COOLDOWN
        boolean hasResendCooldown(String email);

        // ── Reset token (issued ONLY after OTP is verified) ─────────────
        // Generates a one-time random token, binds it to the email, returns it
        String issueResetToken(String email);

        // Resolves a reset token back to the email it belongs to.
        // Returns null if token is invalid/expired/already used.
        String resolveResetToken(String token);

        // Invalidates the token after it has been used (single use only)
        void deleteResetToken(String token);
}