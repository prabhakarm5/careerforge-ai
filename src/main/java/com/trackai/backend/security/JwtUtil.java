package com.trackai.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtUtil {

        // SECRET KEY
        private static final String SECRET =

                        "mysecretkeymysecretkeymysecretkey123456";

        // USER ACCESS TOKEN EXPIRY
        @Value("${app.jwt.access-token-expiry}")
        private Duration accessTokenExpiry;

        // USER REFRESH TOKEN EXPIRY
        @Value("${app.jwt.refresh-token-expiry}")
        private Duration refreshTokenExpiry;

        // ADMIN ACCESS TOKEN EXPIRY
        @Value("${app.admin.access-token-expiry}")
        private Duration adminAccessTokenExpiry;

        // ADMIN REFRESH TOKEN EXPIRY
        @Value("${app.admin.refresh-token-expiry}")
        private Duration adminRefreshTokenExpiry;

        // ADMIN ABSOLUTE SESSION EXPIRY
        @Value("${app.admin.absolute-session-expiry}")
        private Duration adminAbsoluteSessionExpiry;

        // GENERATE SIGN KEY
        private Key getSignKey() {

                return Keys.hmacShaKeyFor(
                                SECRET.getBytes());
        }

        // GENERATE USER ACCESS TOKEN
        public String generateAccessToken(

                        String email,

                        String fingerprint) {

                long expiry =

                                accessTokenExpiry.toMillis();

                return Jwts.builder()

                                .setSubject(email)

                                .claim("type", "ACCESS")

                                .claim("role", "ROLE_USER")

                                .claim(
                                                "fingerprint",
                                                fingerprint)

                                .setIssuedAt(new Date())

                                .setExpiration(

                                                new Date(

                                                                System.currentTimeMillis()

                                                                                + expiry))

                                .signWith(

                                                getSignKey(),

                                                SignatureAlgorithm.HS256)

                                .compact();
        }

        // GENERATE USER REFRESH TOKEN
        public String generateRefreshToken(

                        String email,

                        String fingerprint) {

                long expiry =

                                refreshTokenExpiry.toMillis();

                return Jwts.builder()

                                .setSubject(email)

                                .claim("type", "REFRESH")

                                .claim("role", "ROLE_USER")

                                .claim(
                                                "fingerprint",
                                                fingerprint)

                                .setIssuedAt(new Date())

                                .setExpiration(

                                                new Date(

                                                                System.currentTimeMillis()

                                                                                + expiry))

                                .signWith(

                                                getSignKey(),

                                                SignatureAlgorithm.HS256)

                                .compact();
        }

        // GENERATE ADMIN ACCESS TOKEN
        public String generateAdminAccessToken(

                        String email,

                        String fingerprint) {

                long sessionExpiry =

                                System.currentTimeMillis()

                                                +

                                                adminAbsoluteSessionExpiry.toMillis();

                long accessExpiry =

                                adminAccessTokenExpiry.toMillis();

                return Jwts.builder()

                                .setSubject(email)

                                .claim("type", "ACCESS")

                                .claim("role", "ROLE_ADMIN")

                                .claim(
                                                "fingerprint",
                                                fingerprint)

                                .claim(
                                                "absoluteExpiry",
                                                sessionExpiry)

                                .setIssuedAt(new Date())

                                .setExpiration(

                                                new Date(

                                                                System.currentTimeMillis()

                                                                                + accessExpiry))

                                .signWith(

                                                getSignKey(),

                                                SignatureAlgorithm.HS256)

                                .compact();
        }

        // GENERATE ADMIN REFRESH TOKEN
        public String generateAdminRefreshToken(

                        String email,

                        String fingerprint,

                        long absoluteExpiry) {

                long refreshExpiry =

                                adminRefreshTokenExpiry.toMillis();

                return Jwts.builder()

                                .setSubject(email)

                                .claim("type", "REFRESH")

                                .claim("role", "ROLE_ADMIN")

                                .claim(
                                                "fingerprint",
                                                fingerprint)

                                .claim(
                                                "absoluteExpiry",
                                                absoluteExpiry)

                                .setIssuedAt(new Date())

                                .setExpiration(

                                                new Date(

                                                                System.currentTimeMillis()

                                                                                + refreshExpiry))

                                .signWith(

                                                getSignKey(),

                                                SignatureAlgorithm.HS256)

                                .compact();
        }

        // EXTRACT EMAIL
        public String extractEmail(
                        String token) {

                return extractClaims(token)
                                .getSubject();
        }

        // EXTRACT TOKEN TYPE
        public String extractTokenType(
                        String token) {

                return extractClaims(token)

                                .get(
                                                "type",
                                                String.class);
        }

        // EXTRACT ROLE
        public String extractRole(
                        String token) {

                return extractClaims(token)

                                .get(
                                                "role",
                                                String.class);
        }

        // EXTRACT FINGERPRINT
        public String extractFingerprint(
                        String token) {

                return extractClaims(token)

                                .get(
                                                "fingerprint",
                                                String.class);
        }

        // EXTRACT ABSOLUTE EXPIRY
        public long extractAbsoluteExpiry(
                        String token) {

                Number value =

                                extractClaims(token)

                                                .get(
                                                                "absoluteExpiry",
                                                                Number.class);

                return value.longValue();
        }

        // EXTRACT EXPIRATION
        public Date extractExpiration(
                        String token) {

                return extractClaims(token)

                                .getExpiration();
        }

        // VALIDATE TOKEN
        public boolean validateToken(

                        String token,

                        String email) {

                String extractedEmail =

                                extractEmail(token);

                return extractedEmail.equals(email)

                                &&

                                !isTokenExpired(token);
        }

        // CHECK TOKEN EXPIRY
        private boolean isTokenExpired(
                        String token) {

                return extractClaims(token)

                                .getExpiration()

                                .before(new Date());
        }

        // EXTRACT CLAIMS
        private Claims extractClaims(
                        String token) {

                return Jwts.parserBuilder()

                                .setSigningKey(
                                                getSignKey())

                                .build()

                                .parseClaimsJws(token)

                                .getBody();
        }
}