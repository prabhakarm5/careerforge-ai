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

                // SEND OTP
                forgotPasswordService

                                .sendForgotPasswordOtp(

                                                request.getEmail());

                // RESPONSE
                return ResponseEntity.ok(

                                MessageResponse.builder()

                                                .message(

                                                                "Password reset OTP sent successfully")

                                                .resendAvailableAt(

                                                                LocalDateTime.now()
                                                                                .plusMinutes(2))

                                                .build());
        }

        // VERIFY OTP
        @PostMapping("/verify-reset-otp")
        public ResponseEntity<MessageResponse> verifyResetOtp(

                        @Valid @RequestBody VerifyResetOtpRequest request) {

                // VERIFY OTP
                forgotPasswordService

                                .verifyForgotPasswordOtp(

                                                request.getEmail(),

                                                request.getOtp());

                // RESPONSE
                return ResponseEntity.ok(

                                MessageResponse.builder()

                                                .message(
                                                                "OTP verified successfully")

                                                .build());
        }

        // RESET PASSWORD
        @PostMapping("/reset-password")
        public ResponseEntity<MessageResponse> resetPassword(

                        @Valid @RequestBody ResetPasswordRequest request) {

                // RESET PASSWORD
                forgotPasswordService

                                .resetPassword(

                                                request.getEmail(),

                                                request.getNewPassword());

                // RESPONSE
                return ResponseEntity.ok(

                                MessageResponse.builder()

                                                .message(
                                                                "Password reset successfully")

                                                .build());
        }

        // RESEND OTP
        @PostMapping("/resend-reset-otp")
        public ResponseEntity<MessageResponse> resendResetOtp(

                        @Valid @RequestBody ResendResetOtpRequest request) {

                // RESEND OTP
                forgotPasswordService

                                .resendForgotPasswordOtp(

                                                request.getEmail());

                // RESPONSE
                return ResponseEntity.ok(

                                MessageResponse.builder()

                                                .message(
                                                                "Password reset OTP resend successfully")

                                                .resendAvailableAt(

                                                                LocalDateTime.now()
                                                                                .plusMinutes(2))

                                                .build());
        }
}