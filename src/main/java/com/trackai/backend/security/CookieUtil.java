package com.trackai.backend.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Component
public class CookieUtil {

    // ✅ Kuch bhi hardcoded nahi — sab application-common.yml ke
    // "app.cookie.*" se aa raha hai (dev/prod dono mein same rahega)

    @Value("${app.cookie.refresh-token-name}")
    private String refreshCookieName;

    @Value("${app.cookie.access-token-name}")
    private String accessCookieName;

    @Value("${app.cookie.fingerprint-name}")
    private String fingerprintCookieName;

    @Value("${app.cookie.path}")
    private String cookiePath;

    @Value("${app.cookie.same-site}")
    private String sameSite;

    @Value("${app.cookie.secure}")
    private boolean secure;

    // ============================================================
    // GENERIC HELPERS — saari cookies isi se banti hain, taaki
    // duplicate cookie-building logic kahin na ho
    // ============================================================

    private void setCookie(HttpServletResponse response, String name, String value, Duration maxAge) {

        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true) // ✅ JS access nahi — XSS safe
                .secure(secure) // yml se — prod mein true, agar chaho to dev alag rakh sakte ho
                .sameSite(sameSite) // yml se
                .path(cookiePath) // yml se
                .maxAge(maxAge)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {

        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(cookiePath)
                .maxAge(0) // turant expire
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String extractCookie(HttpServletRequest request, String name) {

        if (request.getCookies() == null) {
            return null;
        }

        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    // ============================================================
    // REFRESH TOKEN COOKIE
    // ============================================================

    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken, Duration maxAge) {
        setCookie(response, refreshCookieName, refreshToken, maxAge);
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        clearCookie(response, refreshCookieName);
    }

    public String extractRefreshTokenFromCookie(HttpServletRequest request) {
        return extractCookie(request, refreshCookieName);
    }

    // ============================================================
    // ACCESS TOKEN COOKIE
    // ============================================================

    public void addAccessTokenCookie(HttpServletResponse response, String accessToken, Duration maxAge) {
        setCookie(response, accessCookieName, accessToken, maxAge);
    }

    public void clearAccessTokenCookie(HttpServletResponse response) {
        clearCookie(response, accessCookieName);
    }

    public String extractAccessTokenFromCookie(HttpServletRequest request) {
        return extractCookie(request, accessCookieName);
    }

    // ============================================================
    // FINGERPRINT COOKIE
    // ============================================================

    public void addFingerprintCookie(HttpServletResponse response, String fingerprint, Duration maxAge) {
        setCookie(response, fingerprintCookieName, fingerprint, maxAge);
    }

    public void clearFingerprintCookie(HttpServletResponse response) {
        clearCookie(response, fingerprintCookieName);
    }

    public String extractFingerprintFromCookie(HttpServletRequest request) {
        return extractCookie(request, fingerprintCookieName);
    }

    // ============================================================
    // LOGOUT — ek call mein teeno cookies clear
    // ============================================================

    public void clearAllAuthCookies(HttpServletResponse response) {
        clearRefreshTokenCookie(response);
        clearAccessTokenCookie(response);
        clearFingerprintCookie(response);
    }
}