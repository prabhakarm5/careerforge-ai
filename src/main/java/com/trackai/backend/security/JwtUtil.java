package com.trackai.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.security.Key;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtUtil {

        // ✅ FIX #1 — Hardcoded secret hata, .env se aa raha hai
        @Value("${app.jwt.secret}")
        private String secret;

        @Value("${app.jwt.access-token-expiry}")
        private Duration accessTokenExpiry;

        @Value("${app.jwt.refresh-token-expiry}")
        private Duration refreshTokenExpiry;

        @Value("${app.admin.access-token-expiry}")
        private Duration adminAccessTokenExpiry;

        @Value("${app.admin.refresh-token-expiry}")
        private Duration adminRefreshTokenExpiry;

        @Value("${app.admin.absolute-session-expiry}")
        private Duration adminAbsoluteSessionExpiry;

        // ✅ FIX #2 — Startup pe secret validate karo
        @PostConstruct
        public void validateSecret() {
                if (secret == null || secret.isBlank()) {
                        throw new IllegalStateException(
                                        "[SECURITY] JWT_SECRET missing in .env. Generate: openssl rand -base64 32");
                }
                if (secret.length() < 32) {
                        throw new IllegalStateException(
                                        "[SECURITY] JWT_SECRET too weak — min 32 chars required.");
                }
        }

        // ✅ FIX #3 — Base64 decode se proper 256-bit key
        private Key getSignKey() {
                byte[] keyBytes = Decoders.BASE64.decode(secret);
                return Keys.hmacShaKeyFor(keyBytes);
        }

        // =========================================
        // TOKEN GENERATION
        // =========================================

        public String generateAccessToken(String email, String fingerprint) {
                return Jwts.builder()
                                .setSubject(email)
                                .claim("type", "ACCESS")
                                .claim("role", "ROLE_USER")
                                .claim("fingerprint", fingerprint)
                                .setIssuedAt(new Date())
                                .setExpiration(new Date(
                                                System.currentTimeMillis() + accessTokenExpiry.toMillis()))
                                // ✅ FIX #4 — Deprecated SignatureAlgorithm hata diya
                                .signWith(getSignKey())
                                .compact();
        }

        public String generateRefreshToken(String email, String fingerprint) {
                return Jwts.builder()
                                .setSubject(email)
                                .claim("type", "REFRESH")
                                .claim("role", "ROLE_USER")
                                .claim("fingerprint", fingerprint)
                                .setIssuedAt(new Date())
                                .setExpiration(new Date(
                                                System.currentTimeMillis() + refreshTokenExpiry.toMillis()))
                                .signWith(getSignKey())
                                .compact();
        }

        public String generateAdminAccessToken(String email, String fingerprint) {
                long sessionExpiry = System.currentTimeMillis()
                                + adminAbsoluteSessionExpiry.toMillis();

                return Jwts.builder()
                                .setSubject(email)
                                .claim("type", "ACCESS")
                                .claim("role", "ROLE_ADMIN")
                                .claim("fingerprint", fingerprint)
                                .claim("absoluteExpiry", sessionExpiry)
                                .setIssuedAt(new Date())
                                .setExpiration(new Date(
                                                System.currentTimeMillis() + adminAccessTokenExpiry.toMillis()))
                                .signWith(getSignKey())
                                .compact();
        }

        public String generateAdminRefreshToken(
                        String email, String fingerprint, long absoluteExpiry) {
                return Jwts.builder()
                                .setSubject(email)
                                .claim("type", "REFRESH")
                                .claim("role", "ROLE_ADMIN")
                                .claim("fingerprint", fingerprint)
                                .claim("absoluteExpiry", absoluteExpiry)
                                .setIssuedAt(new Date())
                                .setExpiration(new Date(
                                                System.currentTimeMillis() + adminRefreshTokenExpiry.toMillis()))
                                .signWith(getSignKey())
                                .compact();
        }

        // =========================================
        // TOKEN EXTRACTION
        // =========================================

        public String extractEmail(String token) {
                return extractClaims(token).getSubject();
        }

        public String extractTokenType(String token) {
                return extractClaims(token).get("type", String.class);
        }

        public String extractRole(String token) {
                return extractClaims(token).get("role", String.class);
        }

        public String extractFingerprint(String token) {
                return extractClaims(token).get("fingerprint", String.class);
        }

        public long extractAbsoluteExpiry(String token) {
                Number value = extractClaims(token).get("absoluteExpiry", Number.class);
                return value.longValue();
        }

        public Date extractExpiration(String token) {
                return extractClaims(token).getExpiration();
        }

        // =========================================
        // TOKEN VALIDATION
        // =========================================

        public boolean validateToken(String token, String email) {
                String extractedEmail = extractEmail(token);
                return extractedEmail.equals(email) && !isTokenExpired(token);
        }

        private boolean isTokenExpired(String token) {
                return extractClaims(token).getExpiration().before(new Date());
        }

        private Claims extractClaims(String token) {
                return Jwts.parserBuilder()
                                .setSigningKey(getSignKey())
                                .build()
                                .parseClaimsJws(token)
                                .getBody();
        }
}