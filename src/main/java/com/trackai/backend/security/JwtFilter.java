package com.trackai.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

        @Override
        protected void doFilterInternal(

                        HttpServletRequest request,

                        HttpServletResponse response,

                        FilterChain filterChain)

                        throws ServletException, IOException {

                // GET REQUEST PATH
                String path = request.getServletPath();

                // SKIP PUBLIC APIs
                if (path.equals("/api/auth/login")
                                ||
                                path.equals("/api/auth/register")) {

                        filterChain.doFilter(
                                        request,
                                        response);

                        return;
                }

                // GET AUTH HEADER
                final String authHeader =

                                request.getHeader(
                                                "Authorization");

                // CHECK HEADER
                if (authHeader == null
                                ||
                                !authHeader.startsWith(
                                                "Bearer ")) {

                        filterChain.doFilter(
                                        request,
                                        response);

                        System.out.println(
                                        "HEADER = " + authHeader);

                        System.out.println(
                                        "REQUEST = " +
                                                        request.getRequestURI());

                        System.out.println(
                                        "AUTH HEADER = " +
                                                        request.getHeader(
                                                                        "Authorization"));

                        return;
                }

                try {

                        // EXTRACT TOKEN
                        final String token =

                                        authHeader.substring(7);
                        System.out.println(
                                        "TOKEN = " + token);

                        // CHECK TOKEN TYPE
                        String tokenType =

                                        jwtUtil.extractTokenType(
                                                        token);

                        // ONLY ACCESS TOKEN ALLOWED
                        if (tokenType == null
                                        ||
                                        !tokenType.equals(
                                                        "ACCESS")) {

                                filterChain.doFilter(
                                                request,
                                                response);

                                return;
                        }

                        // EXTRACT EMAIL
                        String email =

                                        jwtUtil.extractEmail(
                                                        token);
                        System.out.println(
                                        "EMAIL = " + email);

                        // CHECK USER
                        if (email != null
                                        &&
                                        SecurityContextHolder
                                                        .getContext()
                                                        .getAuthentication() == null) {

                                // LOAD USER
                                UserDetails userDetails =

                                                userDetailsService
                                                                .loadUserByUsername(
                                                                                email);
                                System.out.println(
                                                "USER = " +
                                                                userDetails.getUsername());

                                // VALIDATE TOKEN
                                if (jwtUtil.validateToken(

                                                token,

                                                userDetails.getUsername())) {

                                        UsernamePasswordAuthenticationToken authToken =

                                                        new UsernamePasswordAuthenticationToken(

                                                                        userDetails,

                                                                        null,

                                                                        userDetails.getAuthorities());

                                        boolean valid = jwtUtil.validateToken(
                                                        token,
                                                        userDetails.getUsername());

                                        System.out.println(
                                                        "VALID = " + valid);

                                        authToken.setDetails(

                                                        new WebAuthenticationDetailsSource()
                                                                        .buildDetails(
                                                                                        request));
                                        System.out.println(
                                                        "SETTING AUTH");

                                        // SET AUTHENTICATION
                                        SecurityContextHolder
                                                        .getContext()
                                                        .setAuthentication(
                                                                        authToken);
                                        System.out.println(
                                                        SecurityContextHolder
                                                                        .getContext()
                                                                        .getAuthentication());
                                }
                        }

                } catch (Exception e) {

                        // CONSOLE MESSAGE
                        System.out.println(
                                        "JWT TOKEN INVALID OR EXPIRED");

                        // SEND RESPONSE
                        response.setStatus(
                                        HttpServletResponse.SC_UNAUTHORIZED);

                        response.setContentType(
                                        "application/json");

                        response.getWriter().write(

                                        """
                                                        {
                                                            "message":"Invalid or expired token"
                                                        }
                                                        """);

                        return;
                }

                // CONTINUE FILTER
                filterChain.doFilter(
                                request,
                                response);
        }
}