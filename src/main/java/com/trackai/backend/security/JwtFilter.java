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

// NOTE: this file is UNCHANGED from what you already have — it was already
// correct. The crash in your logs was NOT coming from this filter; it was
// coming from Spring Security's own built-in AuthorizationFilter re-running
// on the SSE async-completion dispatch (see SecurityConfig.java for the
// actual fix: DispatcherType.ASYNC/ERROR permitAll()). Keeping this file
// here just for completeness so you have the full, consistent set.
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

        // SSE responses (SseEmitter) trigger an ASYNC DISPATCH when
        // emitter.complete() / completeWithError() runs. Re-running JwtFilter
        // on that redispatch (returning false here) means that if anything
        // about the token looks even slightly off on that second pass, this
        // filter tries to write a 401 onto a response that has ALREADY been
        // committed and flushed as an SSE stream. Returning true (the
        // default OncePerRequestFilter behavior) keeps this filter OUT of
        // the async redispatch — auth was already validated once when the
        // SSE connection opened, nothing needs re-checking on the
        // completion dispatch.
        @Override
        protected boolean shouldNotFilterAsyncDispatch() {
                return true;
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
                // Guard against writing to an already-committed response.
                // Without this, any late 401 attempt on a finished SSE stream
                // throws IllegalStateException and shows up as a server-side
                // crash log even though the client already got its full answer.
                if (response.isCommitted()) {
                        log.debug("Response already committed — skipping 401 write ({})", message);
                        return;
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                                "{\"success\":false,\"message\":\"" + message + "\"}");
        }
}