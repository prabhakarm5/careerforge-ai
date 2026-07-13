package com.trackai.backend.security;

import com.trackai.backend.dto.cache.CachedUser;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.Role;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.repository.WalletRepository;
import com.trackai.backend.service.RedisRefreshTokenService;
import com.trackai.backend.service.RedisUserCacheService;
import com.trackai.backend.service.WalletService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

        private final UserRepository userRepository;
        private final WalletRepository walletRepository;
        private final WalletService walletService;
        private final RedisUserCacheService redisUserCacheService;
        private final RedisRefreshTokenService redisRefreshTokenService;
        private final PasswordEncoder passwordEncoder;
        private final JwtUtil jwtUtil;
        private final CookieUtil cookieUtil;

        @Value("${app.frontend-url}")
        private String frontendUrl;

        @Value("${app.jwt.access-token-expiry}")
        private Duration accessTokenExpiry;

        @Value("${app.jwt.refresh-token-expiry}")
        private Duration refreshTokenExpiry;

        @Override
        @Transactional
        public void onAuthenticationSuccess(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Authentication authentication) throws IOException, ServletException {

                String provider = extractProvider(request);
                OAuth2User principal = (OAuth2User) authentication.getPrincipal();

                try {
                        User user = upsertSocialUser(provider, principal);
                        issueCookieSession(request, response, user);
                        clearTemporaryOAuthSession(request);

                        String successUrl = UriComponentsBuilder
                                        .fromUriString(frontendUrl)
                                        .path("/oauth/success")
                                        .queryParam("provider", provider)
                                        .build()
                                        .toUriString();

                        response.sendRedirect(successUrl);
                } catch (RuntimeException ex) {
                        cookieUtil.clearAllAuthCookies(response);

                        String errorUrl = UriComponentsBuilder
                                        .fromUriString(frontendUrl)
                                        .path("/login")
                                        .queryParam("oauthError", ex.getMessage())
                                        .build()
                                        .toUriString();

                        response.sendRedirect(errorUrl);
                }
        }

        private User upsertSocialUser(String provider, OAuth2User principal) {
                String email = normalizeEmail(principal.getAttribute("email"));
                if (email == null || email.isBlank()) {
                        throw new RuntimeException("Email not available from " + provider + ". Please allow verified email access.");
                }

                User user = userRepository.findByEmail(email).orElse(null);
                boolean created = false;

                if (user == null) {
                        user = createSocialUser(provider, principal, email);
                        created = true;
                } else {
                        if (user.getRole() == Role.ROLE_ADMIN) {
                                throw new RuntimeException("Admin accounts cannot use social login");
                        }

                        if (Boolean.TRUE.equals(user.getBlocked())) {
                                throw new RuntimeException("Account blocked by admin");
                        }

                        // Social provider has already verified the email. If this was a normal
                        // pending signup, activate it instead of forcing the old email OTP path.
                        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                                user.setEmailVerified(true);
                                user.setEnabled(true);
                        } else if (!Boolean.TRUE.equals(user.getEnabled())) {
                                throw new RuntimeException("Your account is disabled");
                        }

                        user.setRole(Role.ROLE_USER);
                        copyFreshProfileData(user, provider, principal);
                        user = userRepository.save(user);
                }

                syncUserCache(user);
                ensureWallet(user.getId(), created);

                return user;
        }

        private User createSocialUser(String provider, OAuth2User principal, String email) {
                String providerId = extractProviderId(provider, principal);

                User user = User.builder()
                                .id(UUID.randomUUID().toString())
                                .name(extractName(principal, email))
                                .email(email)
                                // OAuth users never use this password. A random encoded value keeps
                                // the existing non-null password column intact and prevents password login.
                                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                                // Existing schema requires mobileNumber. Social providers do not give a
                                // phone number, so we store a stable synthetic value until the user edits profile.
                                .mobileNumber("oauth:" + provider + ":" + providerId)
                                .description(null)
                                .profileImage(extractImage(principal))
                                .role(Role.ROLE_USER)
                                .enabled(true)
                                .blocked(false)
                                .emailVerified(true)
                                .createdAt(LocalDateTime.now())
                                .build();

                return userRepository.save(user);
        }

        private void copyFreshProfileData(User user, String provider, OAuth2User principal) {
                String image = extractImage(principal);
                if (image != null && !image.isBlank() && user.getProfileImagePublicId() == null) {
                        user.setProfileImage(image);
                }

                if (user.getMobileNumber() == null || user.getMobileNumber().isBlank()) {
                        user.setMobileNumber("oauth:" + provider + ":" + extractProviderId(provider, principal));
                }
        }

        private void ensureWallet(String userId, boolean justCreated) {
                if (justCreated || walletRepository.findByUserId(userId).isEmpty()) {
                        walletService.createWallet(userId);
                }
        }

        private void issueCookieSession(HttpServletRequest request, HttpServletResponse response, User user) {
                String fingerprint = buildRequestFingerprint(request);
                String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), user.getId(), fingerprint);

                redisRefreshTokenService.saveRefreshToken(user.getEmail(), fingerprint, refreshToken);

                cookieUtil.addRefreshTokenCookie(response, refreshToken, refreshTokenExpiry);
                cookieUtil.addFingerprintCookie(response, fingerprint, refreshTokenExpiry);
        }


        private void clearTemporaryOAuthSession(HttpServletRequest request) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                        session.invalidate();
                }
        }

        private String buildRequestFingerprint(HttpServletRequest request) {
                String userAgent = request.getHeader("User-Agent");
                String forwardedFor = request.getHeader("X-Forwarded-For");
                String ip = forwardedFor != null && !forwardedFor.isBlank()
                                ? forwardedFor.split(",")[0].trim()
                                : request.getRemoteAddr();

                String raw = (userAgent == null ? "unknown" : userAgent) + "|" + (ip == null ? "unknown" : ip);

                try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
                        return "oauth:" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
                } catch (NoSuchAlgorithmException ex) {
                        return "oauth:" + UUID.randomUUID();
                }
        }
        private void syncUserCache(User user) {
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
                                .profileImagePublicId(user.getProfileImagePublicId())
                                .description(user.getDescription())
                                .createdAt(user.getCreatedAt())
                                .build();

                redisUserCacheService.saveUser(cachedUser);
        }

        private String extractProvider(HttpServletRequest request) {
                String uri = request.getRequestURI();
                return uri.substring(uri.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        }

        private String normalizeEmail(Object rawEmail) {
                if (rawEmail == null) {
                        return null;
                }
                return String.valueOf(rawEmail).trim().toLowerCase(Locale.ROOT);
        }

        private String extractProviderId(String provider, OAuth2User principal) {
                Object id = "google".equals(provider)
                                ? principal.getAttribute("sub")
                                : principal.getAttribute("id");

                if (id == null) {
                        id = principal.getName();
                }

                return String.valueOf(id);
        }

        private String extractName(OAuth2User principal, String email) {
                Object name = principal.getAttribute("name");
                if (name != null && !String.valueOf(name).isBlank()) {
                        return String.valueOf(name).trim();
                }

                Object login = principal.getAttribute("login");
                if (login != null && !String.valueOf(login).isBlank()) {
                        return String.valueOf(login).trim();
                }

                return email.substring(0, email.indexOf('@'));
        }

        private String extractImage(OAuth2User principal) {
                Object picture = principal.getAttribute("picture");
                if (picture == null) {
                        picture = principal.getAttribute("avatar_url");
                }
                return picture == null ? null : String.valueOf(picture);
        }
}