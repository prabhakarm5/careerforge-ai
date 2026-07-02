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

        private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

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

        // ✅ NEW FIX — SSE (SseEmitter) responses trigger an ASYNC DISPATCH when
        // emitter.complete()/completeWithError() is called. Spring's
        // OncePerRequestFilter, by default, SKIPS running the filter chain
        // again on that async redispatch (shouldNotFilterAsyncDispatch()
        // returns true by default). That meant JwtFilter never re-ran on the
        // redispatch, SecurityContext ended up empty, and AuthorizationFilter
        // threw AccessDeniedException AFTER the SSE response was already
        // committed — the exact "Unable to handle the Spring Security
        // Exception because the response is already committed" crash seen in
        // the logs. Returning false here forces this filter to run on async
        // dispatch too, so auth stays valid for the whole SSE lifecycle.
        @Override
        protected boolean shouldNotFilterAsyncDispatch() {
                return false;
        }

        @Override
        protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain)
                        throws ServletException, IOException {

                final String authHeader = request.getHeader("Authorization");

                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        log.debug("No Bearer token for path: {}", request.getRequestURI());
                        filterChain.doFilter(request, response);
                        return;
                }

                try {
                        final String token = authHeader.substring(7);

                        String tokenType = jwtUtil.extractTokenType(token);
                        if (tokenType == null || !tokenType.equals("ACCESS")) {
                                log.warn("Invalid token type: {} for path: {}", tokenType, request.getRequestURI());
                                sendUnauthorized(response, "Invalid token type");
                                return;
                        }

                        String email = jwtUtil.extractEmail(token);

                        if (email != null
                                        && SecurityContextHolder.getContext().getAuthentication() == null) {

                                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                                if (jwtUtil.validateToken(token, userDetails.getUsername())) {

                                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                                        userDetails,
                                                        null,
                                                        userDetails.getAuthorities());

                                        authToken.setDetails(
                                                        new WebAuthenticationDetailsSource().buildDetails(request));

                                        SecurityContextHolder.getContext().setAuthentication(authToken);

                                        log.debug("Auth set for user: {}", email);
                                }
                        }

                } catch (Exception e) {
                        log.warn("JWT validation failed for path: {} — {}",
                                        request.getRequestURI(), e.getMessage());
                        sendUnauthorized(response, "Invalid or expired token");
                        return;
                }

                filterChain.doFilter(request, response);
        }

        private void sendUnauthorized(HttpServletResponse response, String message)
                        throws IOException {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                                "{\"success\":false,\"message\":\"" + message + "\"}");
        }
}