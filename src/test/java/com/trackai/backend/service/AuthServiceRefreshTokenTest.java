package com.trackai.backend.service;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.RefreshTokenResponse;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.security.JwtUtil;
import com.trackai.backend.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTokenTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private RedisRefreshTokenService redisRefreshTokenService;
    @Mock private RedisRateLimitService redisRateLimitService;
    @Mock private RateLimitProperties rateLimitProperties;
    @Mock private WalletService walletService;
    @Mock private RedisUserCacheService redisUserCacheService;

    @InjectMocks private AuthServiceImpl authService;

    @BeforeEach
    void allowRefreshRequest() {
        when(rateLimitProperties.getRefreshToken()).thenReturn(new RateLimitProperties.Limit());
        when(redisRateLimitService.allowRequest(anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(RateLimitResponse.builder().allowed(true).build());
    }

    @Test
    void adminRefreshPreservesRoleAndAbsoluteSessionDeadline() {
        String oldToken = "old-admin-refresh";
        long absoluteExpiry = System.currentTimeMillis() + 3_600_000;

        when(jwtUtil.extractTokenType(oldToken)).thenReturn("REFRESH");
        when(jwtUtil.extractEmail(oldToken)).thenReturn("admin@example.com");
        when(jwtUtil.extractFingerprint(oldToken)).thenReturn("device-1");
        when(jwtUtil.extractRole(oldToken)).thenReturn("ROLE_ADMIN");
        when(jwtUtil.extractUserId(oldToken)).thenReturn("admin-id");
        when(jwtUtil.extractAbsoluteExpiry(oldToken)).thenReturn(absoluteExpiry);
        when(jwtUtil.generateAdminAccessToken(
                "admin@example.com", "admin-id", "device-1", absoluteExpiry))
                .thenReturn("new-admin-access");
        when(jwtUtil.generateAdminRefreshToken(
                "admin@example.com", "admin-id", "device-1", absoluteExpiry))
                .thenReturn("new-admin-refresh");
        when(redisRefreshTokenService.rotateRefreshToken(
                "admin@example.com", "device-1", oldToken, "new-admin-refresh"))
                .thenReturn(true);

        RefreshTokenResponse response = authService.refreshAccessToken(oldToken, "device-1");

        assertThat(response.getAccessToken()).isEqualTo("new-admin-access");
        assertThat(response.getRole()).isEqualTo("ROLE_ADMIN");
        verify(jwtUtil, never()).generateAccessToken(anyString(), anyString(), anyString());
        verify(jwtUtil, never()).generateRefreshToken(anyString(), anyString(), anyString());
    }

    @Test
    void userRefreshKeepsUserTokenFamily() {
        String oldToken = "old-user-refresh";

        when(jwtUtil.extractTokenType(oldToken)).thenReturn("REFRESH");
        when(jwtUtil.extractEmail(oldToken)).thenReturn("user@example.com");
        when(jwtUtil.extractFingerprint(oldToken)).thenReturn("device-2");
        when(jwtUtil.extractRole(oldToken)).thenReturn("ROLE_USER");
        when(jwtUtil.extractUserId(oldToken)).thenReturn("user-id");
        when(jwtUtil.generateAccessToken("user@example.com", "user-id", "device-2"))
                .thenReturn("new-user-access");
        when(jwtUtil.generateRefreshToken("user@example.com", "user-id", "device-2"))
                .thenReturn("new-user-refresh");
        when(redisRefreshTokenService.rotateRefreshToken(
                "user@example.com", "device-2", oldToken, "new-user-refresh"))
                .thenReturn(true);

        RefreshTokenResponse response = authService.refreshAccessToken(oldToken, "device-2");

        assertThat(response.getAccessToken()).isEqualTo("new-user-access");
        assertThat(response.getRole()).isEqualTo("ROLE_USER");
        verify(jwtUtil, never()).generateAdminAccessToken(
                anyString(), anyString(), anyString(), anyLong());
    }
}