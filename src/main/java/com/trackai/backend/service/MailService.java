package com.trackai.backend.service;

import org.springframework.scheduling.annotation.Async;

import com.trackai.backend.entity.PaymentTransaction;

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

        // SEND PAYMENT SUCCESS EMAIL
        @Async
        void sendPaymentSuccessEmail(String userName, String toEmail, PaymentTransaction txn, byte[] invoicePdf);

        // CHANGED: added refundSlaDays so the failed-payment email can tell the
        // user exactly how many business days a refund takes, instead of a
        // vague "will be refunded" with no timeline.
        @Async
        void sendPaymentFailedEmail(String userName, String toEmail, PaymentTransaction txn, String reason,
                        byte[] invoicePdf, int refundSlaDays);
}