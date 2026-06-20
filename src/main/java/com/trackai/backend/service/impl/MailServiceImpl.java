package com.trackai.backend.service.impl;

import com.trackai.backend.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailServiceImpl
                implements MailService {

        private final JavaMailSender mailSender;

        @Value("${spring.mail.username}")
        private String fromEmail;

        @Value("${app.mail.from-name}")
        private String fromName;

        // SEND VERIFICATION EMAIL
        @Override
        public void sendVerificationEmail(

                        String userName,

                        String toEmail,

                        String verificationLink,

                        long expiryMinutes) {

                SimpleMailMessage message =

                                new SimpleMailMessage();

                message.setFrom(fromEmail);

                message.setTo(toEmail);

                message.setSubject(
                                "Verify Your TrackAI Account");

                message.setText(

                                "Hello " + userName + ",\n\n"

                                                +

                                                "Welcome to TrackAI.\n\n"

                                                +

                                                "Please verify your email address "
                                                +
                                                "to activate your account.\n\n"

                                                +

                                                "Verification Link:\n\n"

                                                +

                                                verificationLink

                                                +

                                                "\n\n"

                                                +

                                                "This verification link "
                                                +
                                                "will expire in "
                                                +
                                                expiryMinutes
                                                +
                                                " minutes.\n\n"

                                                +

                                                "If you did not create this account, "
                                                +
                                                "please ignore this email.\n\n"

                                                +

                                                "Thanks,\n"

                                                +

                                                fromName);

                mailSender.send(message);
        }

        // SEND RESEND VERIFICATION EMAIL
        @Override
        public void sendResendVerificationEmail(

                        String userName,

                        String toEmail,

                        String verificationLink,

                        long expiryMinutes) {

                SimpleMailMessage message =

                                new SimpleMailMessage();

                message.setFrom(fromEmail);

                message.setTo(toEmail);

                message.setSubject(
                                "New Verification Link - TrackAI");

                message.setText(

                                "Hello " + userName + ",\n\n"

                                                +

                                                "A new verification link "
                                                +
                                                "has been generated for your "
                                                +
                                                "TrackAI account.\n\n"

                                                +

                                                "Verification Link:\n\n"

                                                +

                                                verificationLink

                                                +

                                                "\n\n"

                                                +

                                                "This verification link "
                                                +
                                                "will expire in "
                                                +
                                                expiryMinutes
                                                +
                                                " minutes.\n\n"

                                                +

                                                "If you did not request this, "
                                                +
                                                "please secure your account.\n\n"

                                                +

                                                "Thanks,\n"

                                                +

                                                fromName);

                mailSender.send(message);
        }

        // SEND FORGOT PASSWORD OTP
        @Override
        public void sendForgotPasswordOtp(

                        String userName,

                        String toEmail,

                        String otp,

                        long expiryMinutes) {

                SimpleMailMessage message =

                                new SimpleMailMessage();

                message.setFrom(fromEmail);

                message.setTo(toEmail);

                message.setSubject(
                                "TrackAI Password Reset OTP");

                message.setText(

                                "Hello " + userName + ",\n\n"

                                                +

                                                "We received a request to reset your "
                                                +
                                                "TrackAI account password.\n\n"

                                                +

                                                "Your Password Reset OTP is:\n\n"

                                                +

                                                otp

                                                +

                                                "\n\n"

                                                +

                                                "This OTP will expire in "
                                                +
                                                expiryMinutes
                                                +
                                                " minutes.\n\n"

                                                +

                                                "If you did not request a password reset, "
                                                +
                                                "please ignore this email.\n\n"

                                                +

                                                "Thanks,\n"

                                                +

                                                fromName);

                mailSender.send(message);
        }

        // SEND ADMIN LOGIN OTP
        @Override
        public void sendAdminLoginOtp(

                        String userName,

                        String toEmail,

                        String otp,

                        long expiryMinutes) {

                SimpleMailMessage message =

                                new SimpleMailMessage();

                message.setFrom(fromEmail);

                message.setTo(toEmail);

                message.setSubject(
                                "TrackAI Admin Login OTP");

                message.setText(

                                "Hello " + userName + ",\n\n"

                                                +

                                                "Your admin login OTP is:\n\n"

                                                +

                                                otp

                                                +

                                                "\n\n"

                                                +

                                                "This OTP will expire in "
                                                +
                                                expiryMinutes
                                                +
                                                " minutes.\n\n"

                                                +

                                                "If you did not request this login, "
                                                +
                                                "please secure your account immediately.\n\n"

                                                +

                                                "Thanks,\n"

                                                +

                                                fromName);

                mailSender.send(message);
        }
}