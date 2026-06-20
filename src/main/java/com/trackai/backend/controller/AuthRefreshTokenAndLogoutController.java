package com.trackai.backend.controller;

import com.trackai.backend.dto.LogoutRequest;
import com.trackai.backend.dto.RefreshTokenRequest;
import com.trackai.backend.dto.RefreshTokenResponse;

import com.trackai.backend.service.AuthService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRefreshTokenAndLogoutController {

        private final AuthService authService;

        // REFRESH ACCESS TOKEN
        @PostMapping("/refresh-token")
        public ResponseEntity<RefreshTokenResponse> refreshToken(
                        @Valid @RequestBody RefreshTokenRequest request) {

                return ResponseEntity.ok(

                                authService.refreshAccessToken(
                                                request.getRefreshToken(),
                                                request.getFingerprint()));
        }

        // LOGOUT CURRENT DEVICE
        @PostMapping("/logout")
        public ResponseEntity<Map<String, String>> logout(
                        @Valid @RequestBody LogoutRequest request) {

                // Logout current device
                authService.logout(
                                request.getRefreshToken(),
                                request.getFingerprint());

                return ResponseEntity.ok(
                                Map.of(
                                                "message",
                                                "Logout successful"));
        }

        // LOGOUT ALL DEVICES
        @PostMapping("/logout-all-devices")
        public ResponseEntity<Map<String, String>> logoutAllDevices(
                        @Valid @RequestBody RefreshTokenRequest request) {

                // Logout all devices
                authService.logoutAllDevices(
                                request.getRefreshToken());

                return ResponseEntity.ok(
                                Map.of(
                                                "message",
                                                "Logged out from all devices successfully"));
        }
}