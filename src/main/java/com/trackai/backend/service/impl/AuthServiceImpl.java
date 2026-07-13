package com.trackai.backend.service.impl;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.LoginRequest;
import com.trackai.backend.dto.LoginResponse;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.RefreshTokenResponse;
import com.trackai.backend.dto.RegisterRequest;
import com.trackai.backend.dto.cache.CachedUser;
import com.trackai.backend.dto.cloudinary.CloudinaryUploadResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.Role;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.security.JwtUtil;
import com.trackai.backend.service.AuthService;
import com.trackai.backend.service.CloudinaryService;
import com.trackai.backend.service.EmailVerificationService;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.RedisRefreshTokenService;
import com.trackai.backend.service.RedisUserCacheService;
import com.trackai.backend.service.WalletService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtUtil jwtUtil;
        private final EmailVerificationService emailVerificationService;
        private final CloudinaryService cloudinaryService;
        private final RedisRefreshTokenService redisRefreshTokenService;
        private final RedisRateLimitService redisRateLimitService;
        private final RateLimitProperties rateLimitProperties;
        private final WalletService walletService;
        private final RedisUserCacheService redisUserCacheService;

        // Normalize email
        private String normalizeEmail(String email) {

                return email.trim().toLowerCase();
        }

        // Normalize fingerprint
        private String normalizeFingerprint(String fingerprint) {

                return fingerprint.trim().toLowerCase();
        }

        // Register user
        @Override
        public User registerUser(RegisterRequest request) {

                // Rate limit
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "register:" + request.getEmail(),

                                rateLimitProperties
                                                .getRegister()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getRegister()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getRegister()
                                                .getRefillMinutes());

                // Block request
                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                String email = normalizeEmail(request.getEmail());

                String mobileNumber = request.getMobileNumber().trim();

                // Email exists
                if (userRepository.findByEmail(email).isPresent()) {

                        throw new RuntimeException("Email already registered");
                }

                // Mobile exists
                if (userRepository.findByMobileNumber(mobileNumber).isPresent()) {

                        throw new RuntimeException(
                                        "Mobile number already registered");
                }

                // Upload profile image
                String profileImage = null;

                // FIX: profileImagePublicId bhi track karna zaroori hai
                // (Cloudinary se image delete karne ke liye future mein
                // isi publicId ki zarurat padegi in updateCurrentUser())
                String profileImagePublicId = null;

                if (request.getProfileImage() != null
                                && !request.getProfileImage().isEmpty()) {

                        CloudinaryUploadResponse upload =

                                        cloudinaryService.uploadProfileImage(

                                                        request.getProfileImage()

                                        );

                        profileImage = upload.getSecureUrl();

                        // FIX: publicId bhi capture karo upload response se
                        profileImagePublicId = upload.getPublicId();

                }

                // Create user
                User user = User.builder()
                                .id(UUID.randomUUID().toString())
                                .name(request.getName().trim())
                                .email(email)
                                .password(passwordEncoder.encode(
                                                request.getPassword()))
                                .mobileNumber(mobileNumber)
                                .description(request.getDescription())
                                .profileImage(profileImage)
                                // FIX: entity mein bhi publicId set karo taaki
                                // DB mein save ho aur baad mein delete ke kaam aaye
                                .profileImagePublicId(profileImagePublicId)
                                .role(Role.ROLE_USER)
                                .enabled(false)
                                .blocked(false)
                                .emailVerified(false)
                                .createdAt(LocalDateTime.now())
                                .build();

                // Save user
                User savedUser = userRepository.save(user);

                if (savedUser.getRole() != Role.ROLE_ADMIN) {

                        CachedUser cachedUser = CachedUser.builder()
                                        .id(savedUser.getId())
                                        .name(savedUser.getName())
                                        .email(savedUser.getEmail())
                                        .role(savedUser.getRole())
                                        .enabled(savedUser.getEnabled())
                                        .blocked(savedUser.getBlocked())
                                        .emailVerified(savedUser.getEmailVerified())
                                        .mobileNumber(savedUser.getMobileNumber())
                                        .profileImage(savedUser.getProfileImage())
                                        // FIX: cache mein bhi publicId store karo, warna
                                        // getAuthenticatedUser() cache-hit path pe ye field
                                        // null aayegi aur updateCurrentUser() purani
                                        // Cloudinary image kabhi delete nahi kar payega
                                        .profileImagePublicId(savedUser.getProfileImagePublicId())
                                        .description(savedUser.getDescription())
                                        .createdAt(savedUser.getCreatedAt())
                                        .build();

                        redisUserCacheService.saveUser(cachedUser);
                }

                // Create wallet
                walletService.createWallet(
                                savedUser.getId());

                // Send verification email
                emailVerificationService.sendVerificationEmail(
                                savedUser.getEmail());

                return savedUser;
        }

        // Login user
        @Override
        public LoginResponse loginUser(LoginRequest request) {

                // Rate limit
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "login:" + request.getEmail(),

                                rateLimitProperties
                                                .getLogin()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getLogin()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getLogin()
                                                .getRefillMinutes());

                // Block request
                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                String email = normalizeEmail(request.getEmail());

                String fingerprint = normalizeFingerprint(
                                request.getFingerprint());

                // Find user
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "User is not registered please register the user first"));

                // Block admin login
                if (user.getRole().name().equals("ROLE_ADMIN")) {

                        throw new RuntimeException(
                                        "Admin accounts must use admin OTP login");
                }

                // Password validation
                if (!passwordEncoder.matches(
                                request.getPassword(),
                                user.getPassword())) {

                        throw new RuntimeException(
                                        "Invalid password please check your password");
                }

                // Email verification
                if (!Boolean.TRUE.equals(
                                user.getEmailVerified())) {

                        throw new RuntimeException(
                                        "Please verify your email first");
                }

                // Account disabled
                if (!Boolean.TRUE.equals(
                                user.getEnabled())) {

                        throw new RuntimeException(
                                        "Your account is disabled");
                }

                // Account blocked
                if (Boolean.TRUE.equals(
                                user.getBlocked())) {

                        throw new RuntimeException(
                                        "Account blocked by admin");
                }

                // Generate tokens
                String accessToken = jwtUtil.generateAccessToken(
                                user.getEmail(),
                                user.getId(),
                                fingerprint);

                String refreshToken = jwtUtil.generateRefreshToken(
                                user.getEmail(),
                                user.getId(),
                                fingerprint);

                // Save refresh token
                redisRefreshTokenService.saveRefreshToken(
                                user.getEmail(),
                                fingerprint,
                                refreshToken);

                CachedUser cachedUser = CachedUser.builder()
                                .id(user.getId())
                                .name(user.getName())
                                .email(user.getEmail())
                                .role(user.getRole())
                                .enabled(user.getEnabled())
                                .blocked(user.getBlocked())
                                .emailVerified(user.getEmailVerified())
                                .mobileNumber(user.getMobileNumber())
                                .profileImage(user.getProfileImage())
                                // FIX: login ke waqt bhi cache mein publicId daalo,
                                // taaki login ke turant baad agar user profile image
                                // update kare to cache-hit path se purani image ka
                                // publicId sahi mile aur delete ho sake
                                .profileImagePublicId(user.getProfileImagePublicId())
                                .description(user.getDescription())
                                .createdAt(user.getCreatedAt())
                                .build();

                if (user.getRole() != Role.ROLE_ADMIN) {
                        redisUserCacheService.saveUser(cachedUser);
                }
                // Response
                return LoginResponse.builder()
                                .id(user.getId())
                                .name(user.getName())
                                .email(user.getEmail())
                                .role(user.getRole().name())
                                .accessToken(accessToken)
                                .refreshToken(refreshToken)
                                .fingerprint(fingerprint)
                                .profileImage(user.getProfileImage())

                                .build();
        }

        // Refresh access token
        @Override
        public RefreshTokenResponse refreshAccessToken(
                        String refreshToken,
                        String fingerprint) {

                // Rate limit
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "refresh:" + fingerprint,

                                rateLimitProperties
                                                .getRefreshToken()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getRefreshToken()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getRefreshToken()
                                                .getRefillMinutes());

                // Block request
                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                try {

                        fingerprint = normalizeFingerprint(fingerprint);

                        // Validate token type
                        String tokenType = jwtUtil.extractTokenType(
                                        refreshToken);

                        if (!tokenType.equals("REFRESH")) {

                                throw new RuntimeException(
                                                "Invalid refresh token");
                        }

                        // Extract email
                        String email = normalizeEmail(
                                        jwtUtil.extractEmail(
                                                        refreshToken));

                        String userId = jwtUtil.extractUserId(refreshToken);
                        if (userId == null || userId.isBlank()) {
                                CachedUser cachedUser = redisUserCacheService.getUser(email);
                                userId = cachedUser != null
                                                ? cachedUser.getId()
                                                : userRepository.findByEmail(email)
                                                                .orElseThrow(() -> new RuntimeException("User not found"))
                                                                .getId();
                        }

                        // Generate the replacement first, then atomically compare-and-set it.
                        // This removes separate Redis GET, DELETE and SET round trips.
                        String newAccessToken = jwtUtil.generateAccessToken(
                                        email,
                                        userId,
                                        fingerprint);

                        String newRefreshToken = jwtUtil.generateRefreshToken(
                                        email,
                                        userId,
                                        fingerprint);

                        boolean rotated = redisRefreshTokenService.rotateRefreshToken(
                                        email,
                                        fingerprint,
                                        refreshToken,
                                        newRefreshToken);

                        if (!rotated) {
                                throw new RuntimeException(
                                                "Refresh token expired, revoked, or already rotated");
                        }
                        // Response
                        return RefreshTokenResponse.builder()
                                        .accessToken(newAccessToken)
                                        .refreshToken(newRefreshToken)
                                        .tokenType("Bearer")
                                        .message(
                                                        "Access token refreshed successfully")
                                        .build();

                } catch (Exception e) {

                        throw new RuntimeException(
                                        "Invalid or expired refresh token");
                }
        }

        // Logout user
        @Override
        public void logout(
                        String refreshToken,
                        String fingerprint) {

                // Rate limit
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "logout:" + fingerprint,

                                rateLimitProperties
                                                .getLogout()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getLogout()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getLogout()
                                                .getRefillMinutes());

                // Block request
                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                try {

                        fingerprint = normalizeFingerprint(fingerprint);

                        // Validate token type
                        String tokenType = jwtUtil.extractTokenType(
                                        refreshToken);

                        if (!"REFRESH".equals(tokenType)) {

                                throw new RuntimeException(
                                                "Invalid refresh token");
                        }

                        // Extract email
                        String email = normalizeEmail(
                                        jwtUtil.extractEmail(
                                                        refreshToken));

                        // Validate token
                        boolean validToken = redisRefreshTokenService
                                        .isValidRefreshToken(
                                                        email,
                                                        fingerprint,
                                                        refreshToken);

                        if (!validToken) {

                                throw new RuntimeException(
                                                "Refresh token already expired or revoked");
                        }

                        // Delete token
                        redisRefreshTokenService.deleteRefreshToken(
                                        email,
                                        fingerprint);

                } catch (Exception e) {

                        throw new RuntimeException(
                                        "Invalid refresh token");
                }
        }

        // LOGOUT ALL DEVICES
        @Override
        public void logoutAllDevices(String refreshToken) {

                try {

                        // Extract email
                        String email = jwtUtil.extractEmail(refreshToken);

                        // Validate token
                        boolean valid = jwtUtil.validateToken(
                                        refreshToken,
                                        email);

                        if (!valid) {

                                throw new RuntimeException(
                                                "Invalid refresh token");
                        }

                        // Check token type
                        String tokenType = jwtUtil.extractTokenType(
                                        refreshToken);

                        if (!"REFRESH".equals(tokenType)) {

                                throw new RuntimeException(
                                                "Only refresh token allowed");
                        }

                        // Delete all refresh tokens
                        redisRefreshTokenService
                                        .deleteAllRefreshTokens(email);

                        System.out.println(
                                        "ALL DEVICES LOGGED OUT : "
                                                        + email);

                } catch (Exception e) {

                        throw new RuntimeException(
                                        "Logout all devices failed");
                }
        }
}