package com.trackai.backend.controller;

import com.trackai.backend.dto.*;
import com.trackai.backend.service.ForgotPasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserForgotPasswordController {

        private final ForgotPasswordService forgotPasswordService;

        // SEND OTP
        @PostMapping("/forgot-password")
        public ResponseEntity<MessageResponse> forgotPassword(
                        @Valid @RequestBody ForgotPasswordRequest request) {

                forgotPasswordService.sendForgotPasswordOtp(request.getEmail());
                return ResponseEntity.ok(
                                MessageResponse.builder()
                                                .message("Password reset OTP sent successfully")
                                                .resendAvailableAt(LocalDateTime.now().plusMinutes(2))
                                                .build());
        }

        // VERIFY OTP — returns a one-time reset token
        @PostMapping("/verify-reset-otp")
        public ResponseEntity<VerifyResetOtpResponse> verifyResetOtp(
                        @Valid @RequestBody VerifyResetOtpRequest request) {

                String resetToken = forgotPasswordService.verifyForgotPasswordOtp(
                                request.getEmail(),
                                request.getOtp());

                return ResponseEntity.ok(
                                VerifyResetOtpResponse.builder()
                                                .message("OTP verified successfully")
                                                .resetToken(resetToken)
                                                .build());
        }

        // RESET PASSWORD — requires resetToken, NOT email
        @PostMapping("/reset-password")
        public ResponseEntity<MessageResponse> resetPassword(
                        @Valid @RequestBody ResetPasswordRequest request) {

                forgotPasswordService.resetPassword(
                                request.getResetToken(),
                                request.getNewPassword());

                return ResponseEntity.ok(
                                MessageResponse.builder()
                                                .message("Password reset successfully")
                                                .build());
        }

        // RESEND OTP
        @PostMapping("/resend-reset-otp")
        public ResponseEntity<MessageResponse> resendResetOtp(
                        @Valid @RequestBody ResendResetOtpRequest request) {

                forgotPasswordService.resendForgotPasswordOtp(request.getEmail());

                return ResponseEntity.ok(
                                MessageResponse.builder()
                                                .message("Password reset OTP resend successfully")
                                                .resendAvailableAt(LocalDateTime.now().plusMinutes(2))
                                                .build());
        }
}