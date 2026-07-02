package com.trackai.backend.controller;

import com.trackai.backend.dto.LoginRequest;
import com.trackai.backend.dto.LoginResponse;
import com.trackai.backend.dto.MessageResponse;
import com.trackai.backend.dto.RegisterRequest;
import com.trackai.backend.dto.ResendVerificationRequest;
import com.trackai.backend.service.AuthService;
import com.trackai.backend.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserAuthController {

        @Value("${app.mail.resend-wait-minutes}")
        private long resendWaitMinutes;

        private final AuthService authService;

        private final EmailVerificationService emailVerificationService;

        // REGISTER USER
        @PostMapping("/register")
        public ResponseEntity<MessageResponse> register(

                        @ModelAttribute @Valid RegisterRequest request) {

                authService.registerUser(request);

                return ResponseEntity.ok(

                                MessageResponse.builder()

                                                .message(

                                                                "Registration successful. "
                                                                                +
                                                                                "Verification email sent. "
                                                                                +
                                                                                "Please verify your email within 15 minutes.")

                                                .resendAvailableAt(

                                                                LocalDateTime.now()

                                                                                .plusMinutes(
                                                                                                resendWaitMinutes))

                                                .build());
        }

        // LOGIN USER
        @PostMapping("/login")
        public ResponseEntity<LoginResponse> login(

                        @Valid @RequestBody LoginRequest request) {

                return ResponseEntity.ok(authService.loginUser(request));
        }

        // VERIFY EMAIL
        @GetMapping("/verify")
        public ResponseEntity<MessageResponse> verifyEmail(

                        @RequestParam String token) {

                emailVerificationService
                                .verifyToken(token);

                return ResponseEntity.ok(

                                MessageResponse.builder()

                                                .message(
                                                                "Email verified successfully")

                                                .build());
        }

        // RESEND VERIFICATION EMAIL
        @PostMapping("/resend-verification")
        public ResponseEntity<MessageResponse> resendVerificationEmail(

                        @Valid @RequestBody ResendVerificationRequest request) {

                // RESEND EMAIL
                emailVerificationService
                                .resendVerificationEmail(

                                                request.getEmail());

                // RESPONSE
                return ResponseEntity.ok(

                                MessageResponse.builder()

                                                .message(

                                                                "Verification email sent successfully")

                                                .resendAvailableAt(

                                                                LocalDateTime.now()

                                                                                .plusMinutes(
                                                                                                resendWaitMinutes))

                                                .build());
        }

}