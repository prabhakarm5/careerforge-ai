package com.trackai.backend.controller;

import com.trackai.backend.dto.*;
import com.trackai.backend.security.CookieUtil;
import com.trackai.backend.service.AdminOtpLoginService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
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
        // Admin refresh-cookie lifetime is configured centrally in application-common.yml.

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

                // ✅ Fingerprint — httpOnly cookie (admin refresh/logout endpoints
                // isi se fingerprint read karenge, body se nahi)
                cookieUtil.addFingerprintCookie(
                                httpResponse,
                                result.getFingerprint(),
                                adminRefreshTokenExpiry);
                // Refresh token and fingerprint remain HttpOnly; access token is returned
                // once and held only in frontend memory.
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

        @GetMapping("/admin-login-otp/reveal")
        public ResponseEntity<AdminOtpRevealResponse> revealAdminLoginOtp(@RequestParam String token) {
                return ResponseEntity.ok()
                                .cacheControl(CacheControl.noStore())
                                .header(HttpHeaders.PRAGMA, "no-cache")
                                .header("Referrer-Policy", "no-referrer")
                                .header("X-Robots-Tag", "noindex, nofollow")
                                .body(adminOtpLoginService.revealAdminLoginOtp(token));
        }}