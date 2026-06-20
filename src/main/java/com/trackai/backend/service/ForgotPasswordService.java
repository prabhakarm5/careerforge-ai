package com.trackai.backend.service;

public interface ForgotPasswordService {

    void sendForgotPasswordOtp(
            String email);

    void verifyForgotPasswordOtp(
            String email,
            String otp);

    void resetPassword(
            String email,
            String newPassword);

    void resendForgotPasswordOtp(
            String email);

}
