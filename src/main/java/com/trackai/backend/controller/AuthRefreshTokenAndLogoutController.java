package com.trackai.backend.controller;

import com.trackai.backend.dto.RefreshTokenResponse;
import com.trackai.backend.exception.InvalidCredentialsException;
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

        @Value("${app.admin.refresh-token-expiry}")
        private Duration adminRefreshTokenExpiry;

        // ============================================================
        // REFRESH ACCESS TOKEN
        // ============================================================
        //
        // ⚠️ IMPORTANT — is endpoint ka rotation-behavior AuthService ke
        // andar define hota hai (authService.refreshAccessToken). Agar
        // wahan purana refresh token turant hard-delete/invalidate hota
        // hai (no grace window, no reuse-detection family), toh
        // multi-tab ya near-simultaneous-401 scenario mein legitimate
        // requests bhi fail ho sakti hain — kyunki 2 requests almost
        // saath mein purane (same) refresh-token cookie ke saath yahan
        // aa sakti hain, aur dusri wali ko "already rotated/invalid"
        // milega. Frontend isko "session expired" samajh ke logout kar
        // dega, jabki session bilkul valid tha.
        //
        // AuthService.refreshAccessToken() mein grace-window + reuse
        // detection add karna recommended hai:
        // 1. Har refresh token ek "family id" ke saath store karo
        // 2. Rotation ke time purane token ko turant delete mat karo —
        // usse "consumed" mark karo, aur 3-5 second grace window do
        // 3. Grace window ke andar agar wahi purana token dobara aaye
        // (race condition), toh naya generate mat karo — jo pehle
        // hi issue kiya tha wahi wapas do
        // 4. Grace window ke BAHAR agar purana token use ho, toh yeh
        // genuine reuse/theft attempt hai — poori family revoke
        // karo aur user ko forcibly logout karo
        @PostMapping("/refresh-token")
        public ResponseEntity<RefreshTokenResponse> refreshToken(
                        HttpServletRequest httpRequest,
                        HttpServletResponse httpResponse) {

                // ✅ Refresh token + fingerprint dono ab cookie se aayenge, body se nahi
                String refreshTokenFromCookie = cookieUtil.extractRefreshTokenFromCookie(httpRequest);
                String fingerprintFromCookie = cookieUtil.extractFingerprintFromCookie(httpRequest);

                if (refreshTokenFromCookie == null) {
                        throw new InvalidCredentialsException(
                                        "No refresh token found. Please login again.");
                }

                if (fingerprintFromCookie == null) {
                        throw new InvalidCredentialsException(
                                        "No device fingerprint found. Please login again.");
                }

                RefreshTokenResponse result = authService.refreshAccessToken(
                                refreshTokenFromCookie,
                                fingerprintFromCookie);

                // ✅ Token rotation — naya refresh token aur access token phir se cookie mein
                cookieUtil.addRefreshTokenCookie(
                                httpResponse,
                                result.getRefreshToken(),
                                "ROLE_ADMIN".equals(result.getRole())
                                                ? adminRefreshTokenExpiry
                                                : refreshTokenExpiry);
                // Access token cookie intentionally set nahi hoti; body se frontend memory mein jayega.

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