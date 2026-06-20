package com.trackai.backend.service;

import com.trackai.backend.dto.LoginResponse;

public interface AdminOtpLoginService {

        // SEND ADMIN LOGIN OTP
        void sendAdminLoginOtp(

                        String email,

                        String password);

        // VERIFY ADMIN LOGIN OTP
        LoginResponse verifyAdminLoginOtp(

                        String email,

                        String otp,

                        String fingerprint);

        // RESEND ADMIN LOGIN OTP
        void resendAdminLoginOtp(
                        String email);
}