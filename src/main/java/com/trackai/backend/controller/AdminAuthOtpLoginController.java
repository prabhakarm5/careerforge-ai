package com.trackai.backend.controller;

import com.trackai.backend.dto.*;
import com.trackai.backend.security.CookieUtil;
import com.trackai.backend.service.AdminOtpLoginService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AdminAuthOtpLoginController {

        private final AdminOtpLoginService adminOtpLoginService;
        private final CookieUtil cookieUtil;

        // ✅ FIX — pehle static final Duration bina initialize kiye declare tha
        // (compile error) aur neeche galat naam (_DURATION suffix missing) use
        // ho raha tha. Ab dono expiry yml se @Value se aa rahi hain — kahin
        // bhi hardcoded nahi.
        @Value("${app.admin.access-token-expiry}")
        private Duration adminAccessTokenExpiry;

        @Value("${app.admin.refresh-token-expiry}")
        private Duration adminRefreshTokenExpiry;

        // ✅ FIX — pehle "plusMinutes(2)" hardcoded tha, ab yml ke
        // app.otp.resend-wait-minutes se aa raha hai
        @Value("${app.otp.resend-wait-minutes}")
        private long otpResendWaitMinutes;

        // SEND ADMIN LOGIN OTP
        @PostMapping("/admin-login")
        public ResponseEntity<MessageResponse> adminLogin(
                        @Valid @RequestBody AdminLoginRequest request) {

                adminOtpLoginService.sendAdminLoginOtp(
                                request.getEmail(),
                                request.getPassword());

                return ResponseEntity.ok(
                                MessageResponse.builder()
                                                .message("Admin login OTP sent successfully")
                                                .resendAvailableAt(
                                                                LocalDateTime.now()
                                                                                .plusMinutes(otpResendWaitMinutes))
                                                .build());
        }

        // VERIFY ADMIN LOGIN OTP
        @PostMapping("/verify-admin-login-otp")
        public ResponseEntity<LoginResponse> verifyAdminLoginOtp(
                        @Valid @RequestBody VerifyAdminLoginOtpRequest request,
                        HttpServletResponse httpResponse) {

                LoginResponse result = adminOtpLoginService.verifyAdminLoginOtp(
                                request.getEmail(),
                                request.getOtp(),
                                request.getFingerprint());

                // ✅ Admin refresh token — httpOnly cookie
                cookieUtil.addRefreshTokenCookie(
                                httpResponse,
                                result.getRefreshToken(),
                                adminRefreshTokenExpiry);

                // ✅ Admin access token — httpOnly cookie (body mein nahi jayega)
                cookieUtil.addAccessTokenCookie(
                                httpResponse,
                                result.getAccessToken(),
                                adminAccessTokenExpiry);

                // ✅ Fingerprint — httpOnly cookie (admin refresh/logout endpoints
                // isi se fingerprint read karenge, body se nahi)
                cookieUtil.addFingerprintCookie(
                                httpResponse,
                                result.getFingerprint(),
                                adminRefreshTokenExpiry);

                // refreshToken/accessToken/fingerprint — teeno @JsonIgnore honi chahiye
                // LoginResponse DTO mein, taaki body mein na jaayein
                return ResponseEntity.ok(result);
        }

        // RESEND ADMIN LOGIN OTP
        @PostMapping("/resend-admin-login-otp")
        public ResponseEntity<MessageResponse> resendAdminLoginOtp(
                        @Valid @RequestBody ResendAdminLoginOtpRequest request) {

                adminOtpLoginService.resendAdminLoginOtp(request.getEmail());

                return ResponseEntity.ok(
                                MessageResponse.builder()
                                                .message("Admin login OTP resent successfully")
                                                .resendAvailableAt(
                                                                LocalDateTime.now()
                                                                                .plusMinutes(otpResendWaitMinutes))
                                                .build());
        }
}