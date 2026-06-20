package com.trackai.backend.service;

public interface RedisForgotPasswordOtpService {

        // SAVE OTP
        void saveOtp(

                        String email,

                        String otp);

        // GET OTP
        String getOtp(
                        String email);

        // DELETE OTP
        void deleteOtp(
                        String email);

        // SAVE RESEND COOLDOWN
        void saveResendCooldown(
                        String email);

        // CHECK RESEND COOLDOWN
        boolean hasResendCooldown(
                        String email);

        // SAVE VERIFIED STATE
        void saveVerifiedState(String email);

        // CHECK VERIFIED STATE
        boolean isOtpVerified(String email);

        // DELETE VERIFIED STATE
        void deleteVerifiedState(String email);
}
