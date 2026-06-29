package com.trackai.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

        private final JwtUtil jwtUtil;
        private final CustomUserDetailsService userDetailsService;

        // ✅ FIX #1 — System.out.println hata, proper logger use karo
        private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

        // ✅ FIX #2 — shouldNotFilter use karo (tera original approach sahi tha)
        // Public paths yahan — doFilterInternal mein path check ki zaroorat nahi
        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
                String path = request.getServletPath();
                return path.startsWith("/api/auth/login")
                                || path.startsWith("/api/auth/register")
                                || path.startsWith("/api/auth/refresh-token")
                                || path.startsWith("/api/auth/verify")
                                || path.startsWith("/api/auth/resend-verification")
                                || path.startsWith("/api/auth/forgot-password")
                                || path.startsWith("/api/auth/verify-reset-otp")
                                || path.startsWith("/api/auth/reset-password")
                                || path.startsWith("/api/auth/resend-reset-otp")
                                || path.startsWith("/api/plans");
        }

        @Override
        protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain)
                        throws ServletException, IOException {

                // GET AUTH HEADER
                final String authHeader = request.getHeader("Authorization");

                // NO TOKEN — pass through, Spring Security handle karega
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        // ✅ FIX #3 — Token/header log nahi karo (security risk tha)
                        // Sirf path log karo debug ke liye
                        log.debug("No Bearer token for path: {}", request.getRequestURI());
                        filterChain.doFilter(request, response);
                        return;
                }

                try {
                        // EXTRACT TOKEN
                        final String token = authHeader.substring(7);

                        // ✅ FIX #4 — Token kabhi log mat karo — ye sabse bada risk tha
                        // logger.info("TOKEN = {}", token); ← YE BILKUL MAT KARO

                        // CHECK TOKEN TYPE — sirf ACCESS token allow karo
                        String tokenType = jwtUtil.extractTokenType(token);
                        if (tokenType == null || !tokenType.equals("ACCESS")) {
                                log.warn("Invalid token type: {} for path: {}", tokenType, request.getRequestURI());
                                sendUnauthorized(response, "Invalid token type");
                                return;
                        }

                        // EXTRACT EMAIL
                        String email = jwtUtil.extractEmail(token);

                        // SET AUTHENTICATION — sirf agar already set nahi hai
                        if (email != null
                                        && SecurityContextHolder.getContext().getAuthentication() == null) {

                                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                                // ✅ FIX #5 — validateToken sirf ek baar (tera code mein duplicate tha)
                                if (jwtUtil.validateToken(token, userDetails.getUsername())) {

                                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                                        userDetails,
                                                        null,
                                                        userDetails.getAuthorities());

                                        authToken.setDetails(
                                                        new WebAuthenticationDetailsSource().buildDetails(request));

                                        SecurityContextHolder.getContext().setAuthentication(authToken);

                                        // ✅ Sensitive info log nahi — sirf email log karo DEBUG level pe
                                        log.debug("Auth set for user: {}", email);
                                }
                        }

                } catch (Exception e) {
                        // ✅ FIX #6 — Exception message log karo, token nahi
                        log.warn("JWT validation failed for path: {} — {}",
                                        request.getRequestURI(), e.getMessage());
                        sendUnauthorized(response, "Invalid or expired token");
                        return;
                }

                // CONTINUE CHAIN
                filterChain.doFilter(request, response);
        }

        // ✅ Clean helper method — consistent error response
        private void sendUnauthorized(HttpServletResponse response, String message)
                        throws IOException {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                                "{\"success\":false,\"message\":\"" + message + "\"}");
        }
}