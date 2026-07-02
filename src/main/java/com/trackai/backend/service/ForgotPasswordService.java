package com.trackai.backend.service;

public interface ForgotPasswordService {

        void sendForgotPasswordOtp(String email);

        // Now returns a one-time reset token instead of void.
        // This token is the ONLY thing the frontend can use to reset the password.
        String verifyForgotPasswordOtp(String email, String otp);

        // Takes resetToken instead of email — email is resolved server-side from the
        // token.
        void resetPassword(String resetToken, String newPassword);

        void resendForgotPasswordOtp(String email);

}