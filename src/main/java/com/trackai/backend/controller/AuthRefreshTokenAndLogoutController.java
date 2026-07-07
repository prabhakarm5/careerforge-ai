package com.trackai.backend.controller;

import com.trackai.backend.dto.RefreshTokenResponse;
import com.trackai.backend.security.CookieUtil;
import com.trackai.backend.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRefreshTokenAndLogoutController {

        private final AuthService authService;
        private final CookieUtil cookieUtil;

        // ✅ FIX — pehle yahan "private static final Duration
        // REFRESH_TOKEN_COOKIE_EXPIRY
        // = Duration.ofDays(7);" hardcoded tha. Ab yml se aayega, taaki
        // application-common.yml ke "app.jwt.refresh-token-expiry" (7d) ke saath
        // hamesha sync rahe — kahin bhi alag value likhne ki zaroorat nahi.
        @Value("${app.jwt.access-token-expiry}")
        private Duration accessTokenExpiry;

        @Value("${app.jwt.refresh-token-expiry}")
        private Duration refreshTokenExpiry;

        // ============================================================
        // REFRESH ACCESS TOKEN
        // ============================================================
        @PostMapping("/refresh-token")
        public ResponseEntity<RefreshTokenResponse> refreshToken(
                        HttpServletRequest httpRequest,
                        HttpServletResponse httpResponse) {

                // ✅ Refresh token + fingerprint dono ab cookie se aayenge, body se nahi
                String refreshTokenFromCookie = cookieUtil.extractRefreshTokenFromCookie(httpRequest);
                String fingerprintFromCookie = cookieUtil.extractFingerprintFromCookie(httpRequest);

                if (refreshTokenFromCookie == null) {
                        throw new RuntimeException(
                                        "No refresh token found — please login again");
                }

                if (fingerprintFromCookie == null) {
                        throw new RuntimeException(
                                        "No fingerprint found — please login again");
                }

                RefreshTokenResponse result = authService.refreshAccessToken(
                                refreshTokenFromCookie,
                                fingerprintFromCookie);

                // ✅ Token rotation — naya refresh token aur access token phir se cookie mein
                cookieUtil.addRefreshTokenCookie(
                                httpResponse,
                                result.getRefreshToken(),
                                refreshTokenExpiry);

                cookieUtil.addAccessTokenCookie(
                                httpResponse,
                                result.getAccessToken(),
                                accessTokenExpiry);

                // fingerprint cookie same rehti hai (rotate nahi hoti), isliye dobara set
                // karne ki zaroorat nahi — chhod do jaisi hai

                return ResponseEntity.ok(result);
        }

        // ============================================================
        // LOGOUT CURRENT DEVICE
        // ============================================================
        @PostMapping("/logout")
        public ResponseEntity<Map<String, String>> logout(
                        HttpServletRequest httpRequest,
                        HttpServletResponse httpResponse) {

                String refreshTokenFromCookie = cookieUtil.extractRefreshTokenFromCookie(httpRequest);
                String fingerprintFromCookie = cookieUtil.extractFingerprintFromCookie(httpRequest);

                if (refreshTokenFromCookie == null) {
                        throw new RuntimeException("No active session found");
                }

                authService.logout(
                                refreshTokenFromCookie,
                                fingerprintFromCookie);

                // ✅ teeno cookies ek call mein clear
                cookieUtil.clearAllAuthCookies(httpResponse);

                return ResponseEntity.ok(
                                Map.of("message", "Logout successful"));
        }

        // ============================================================
        // LOGOUT ALL DEVICES
        // ============================================================
        @PostMapping("/logout-all-devices")
        public ResponseEntity<Map<String, String>> logoutAllDevices(
                        HttpServletRequest httpRequest,
                        HttpServletResponse httpResponse) {

                String refreshTokenFromCookie = cookieUtil.extractRefreshTokenFromCookie(httpRequest);

                if (refreshTokenFromCookie == null) {
                        throw new RuntimeException("No active session found");
                }

                authService.logoutAllDevices(refreshTokenFromCookie);

                cookieUtil.clearAllAuthCookies(httpResponse);

                return ResponseEntity.ok(
                                Map.of("message", "Logged out from all devices successfully"));
        }
}