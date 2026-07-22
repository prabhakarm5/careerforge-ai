package com.trackai.backend.service;

import com.trackai.backend.dto.AdminOtpRevealResponse;
import com.trackai.backend.dto.LoginResponse;

public interface AdminOtpLoginService {
    void sendAdminLoginOtp(String email, String password);
    LoginResponse verifyAdminLoginOtp(String email, String otp, String fingerprint);
    void resendAdminLoginOtp(String email);
    AdminOtpRevealResponse revealAdminLoginOtp(String token);
}