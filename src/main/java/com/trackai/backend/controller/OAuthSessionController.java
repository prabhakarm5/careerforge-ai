package com.trackai.backend.controller;

import com.trackai.backend.dto.LoginResponse;
import com.trackai.backend.dto.RefreshTokenResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.security.CookieUtil;
import com.trackai.backend.security.JwtUtil;
import com.trackai.backend.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class OAuthSessionController {

        private final AuthService authService;
        private final CookieUtil cookieUtil;
        private final JwtUtil jwtUtil;
        private final UserRepository userRepository;

        @Value("${app.jwt.refresh-token-expiry}")
        private Duration refreshTokenExpiry;

        @PostMapping("/oauth-session")
        public ResponseEntity<LoginResponse> completeOAuthSession(
                        HttpServletRequest httpRequest,
                        HttpServletResponse httpResponse) {

                String refreshTokenFromCookie = cookieUtil.extractRefreshTokenFromCookie(httpRequest);
                String fingerprintFromCookie = cookieUtil.extractFingerprintFromCookie(httpRequest);

                if (refreshTokenFromCookie == null || fingerprintFromCookie == null) {
                        throw new RuntimeException("Social login session expired. Please try again.");
                }

                // OAuth redirect can only set httpOnly cookies. This endpoint turns that
                // cookie session into the normal frontend in-memory access token flow.
                RefreshTokenResponse tokenResponse = authService.refreshAccessToken(
                                refreshTokenFromCookie,
                                fingerprintFromCookie);

                cookieUtil.addRefreshTokenCookie(
                                httpResponse,
                                tokenResponse.getRefreshToken(),
                                refreshTokenExpiry);

                String email = jwtUtil.extractEmail(tokenResponse.getAccessToken());
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("Social login user not found"));

                if (Boolean.TRUE.equals(user.getBlocked())) {
                        throw new RuntimeException("Account blocked by admin");
                }

                if (!Boolean.TRUE.equals(user.getEnabled())) {
                        throw new RuntimeException("Your account is disabled");
                }

                return ResponseEntity.ok(
                                LoginResponse.builder()
                                                .id(user.getId())
                                                .name(user.getName())
                                                .email(user.getEmail())
                                                .role(user.getRole().name())
                                                .accessToken(tokenResponse.getAccessToken())
                                                .profileImage(user.getProfileImage())
                                                .build());
        }
}