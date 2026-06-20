package com.trackai.backend.controller;

import com.trackai.backend.dto.*;

import com.trackai.backend.service.AdminOtpLoginService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AdminAuthOtpLoginController {

        private final AdminOtpLoginService adminOtpLoginService;

        // SEND ADMIN LOGIN OTP
        @PostMapping("/admin-login")
        public ResponseEntity<MessageResponse> adminLogin(

                        @Valid @RequestBody AdminLoginRequest request) {

                // SEND OTP
                adminOtpLoginService

                                .sendAdminLoginOtp(

                                                request.getEmail(),

                                                request.getPassword());

                // RESPONSE
                return ResponseEntity.ok(

                                MessageResponse.builder()

                                                .message(

                                                                "Admin login OTP sent successfully")

                                                .resendAvailableAt(

                                                                LocalDateTime.now()
                                                                                .plusMinutes(2))

                                                .build());
        }

        // VERIFY ADMIN LOGIN OTP
        @PostMapping("/verify-admin-login-otp")
        public ResponseEntity<LoginResponse> verifyAdminLoginOtp(

                        @Valid @RequestBody VerifyAdminLoginOtpRequest request) {

                return ResponseEntity.ok(

                                adminOtpLoginService

                                                .verifyAdminLoginOtp(

                                                                request.getEmail(),

                                                                request.getOtp(),

                                                                request.getFingerprint()));
        }

        // RESEND ADMIN LOGIN OTP
        @PostMapping("/resend-admin-login-otp")
        public ResponseEntity<MessageResponse> resendAdminLoginOtp(

                        @Valid @RequestBody ResendAdminLoginOtpRequest request) {

                // RESEND OTP
                adminOtpLoginService

                                .resendAdminLoginOtp(

                                                request.getEmail());

                // RESPONSE
                return ResponseEntity.ok(

                                MessageResponse.builder()

                                                .message(

                                                                "Admin login OTP resent successfully")

                                                .resendAvailableAt(

                                                                LocalDateTime.now()
                                                                                .plusMinutes(2))

                                                .build());
        }
}