package com.trackai.backend.service;

public interface RedisAdminOtpService {
    void saveOtp(String email, String otp);
    String getOtp(String email);
    void deleteOtp(String email);
    void saveResendCooldown(String email);
    boolean hasResendCooldown(String email);

    // The email link contains only this opaque token; the OTP never appears in the URL.
    void saveRevealToken(String email, String token);
    String getEmailByRevealToken(String token);
    long getOtpTtlSeconds(String email);
}