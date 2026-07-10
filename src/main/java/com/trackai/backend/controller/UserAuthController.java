package com.trackai.backend.controller;

import com.trackai.backend.dto.LoginRequest;
import com.trackai.backend.dto.LoginResponse;
import com.trackai.backend.dto.MessageResponse;
import com.trackai.backend.dto.RegisterRequest;
import com.trackai.backend.dto.ResendVerificationRequest;
import com.trackai.backend.security.CookieUtil;
import com.trackai.backend.service.AuthService;
import com.trackai.backend.service.EmailVerificationService;

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
@RequiredArgsConstructor // ✅ sirf yahi constructor — manual constructor hata diya (wahi error de raha
                         // tha)
public class UserAuthController {

        private final CookieUtil cookieUtil;
        private final AuthService authService;
        private final EmailVerificationService emailVerificationService;

        @Value("${app.mail.resend-wait-minutes}")
        private long resendWaitMinutes;

        // ✅ Koi hardcoded Duration.ofDays(7) nahi — dono expiry yml se aa rahi hain.
        // Spring Duration binding "15m" / "7d" jaisi values ko khud parse kar leta hai
        // (RateLimitProperties / AdminOtpLoginServiceImpl mein bhi isi tarah use ho
        // raha hai).
        @Value("${app.jwt.access-token-expiry}")
        private Duration accessTokenExpiry;

        @Value("${app.jwt.refresh-token-expiry}")
        private Duration refreshTokenExpiry;

        // REGISTER USER
        @PostMapping("/register")
        public ResponseEntity<MessageResponse> register(
                        @ModelAttribute @Valid RegisterRequest request) {

                authService.registerUser(request);

                return ResponseEntity.ok(
                                MessageResponse.builder()
                                                .message(
                                                                "Registration successful. "
                                                                                + "Verification email sent. "
                                                                                + "Please verify your email within 15 minutes.")
                                                .resendAvailableAt(
                                                                LocalDateTime.now()
                                                                                .plusMinutes(resendWaitMinutes))
                                                .build());
        }

        // LOGIN USER
        @PostMapping("/login")
        public ResponseEntity<LoginResponse> login(
                        @Valid @RequestBody LoginRequest request,
                        HttpServletResponse httpResponse) {

                LoginResponse result = authService.loginUser(request);

                // ✅ Refresh token — httpOnly cookie
                cookieUtil.addRefreshTokenCookie(
                                httpResponse,
                                result.getRefreshToken(),
                                refreshTokenExpiry);
                // Access token response body mein rahega; frontend use in-memory store mein rakhega.

                // ✅ Fingerprint — httpOnly cookie (refresh/logout ke waqt yahi se milega,
                // request body mein dobara bhejne ki zaroorat nahi rahegi)
                cookieUtil.addFingerprintCookie(
                                httpResponse,
                                result.getFingerprint(),
                                refreshTokenExpiry); // fingerprint tab tak valid jab tak session valid hai

                // ⚠️ NOTE: LoginResponse DTO mein accessToken, refreshToken, fingerprint
                // teeno fields pe @JsonIgnore lagana zaroori hai, warna ye response body
                // mein bhi chale jayenge (cookie ke saath saath — jo hum nahi chahte)
                return ResponseEntity.ok(result);
        }

        // VERIFY EMAIL
        @GetMapping("/verify")
        public ResponseEntity<MessageResponse> verifyEmail(
                        @RequestParam String token) {

                emailVerificationService.verifyToken(token);

                return ResponseEntity.ok(
                                MessageResponse.builder()
                                                .message("Email verified successfully")
                                                .build());
        }

        // RESEND VERIFICATION EMAIL
        @PostMapping("/resend-verification")
        public ResponseEntity<MessageResponse> resendVerificationEmail(
                        @Valid @RequestBody ResendVerificationRequest request) {

                emailVerificationService.resendVerificationEmail(request.getEmail());

                return ResponseEntity.ok(
                                MessageResponse.builder()
                                                .message("Verification email sent successfully")
                                                .resendAvailableAt(
                                                                LocalDateTime.now()
                                                                                .plusMinutes(resendWaitMinutes))
                                                .build());
        }
}