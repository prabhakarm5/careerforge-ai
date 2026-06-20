package com.trackai.backend.service;

public interface MailService {

    // SEND VERIFICATION EMAIL
    void sendVerificationEmail(

            String userName,

            String toEmail,

            String verificationLink,

            long expiryMinutes);

    // SEND RESEND VERIFICATION EMAIL
    void sendResendVerificationEmail(

            String userName,

            String toEmail,

            String verificationLink,

            long expiryMinutes);

    // SEND FORGOT PASSWORD OTP for user
    void sendForgotPasswordOtp(

            String userName,

            String toEmail,

            String otp,

            long expiryMinutes);

    // SEND ADMIN LOGIN OTP
    void sendAdminLoginOtp(

            String userName,

            String toEmail,

            String otp,

            long expiryMinutes);
}