package com.trackai.backend.config;

import com.trackai.backend.security.JwtAccessDeniedHandler;
import com.trackai.backend.security.JwtAuthenticationEntryPoint;
import com.trackai.backend.security.JwtFilter;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor

public class SecurityConfig {

        private final JwtFilter jwtFilter;

        private final JwtAuthenticationEntryPoint authenticationEntryPoint;

        private final JwtAccessDeniedHandler accessDeniedHandler;

        // PASSWORD ENCODER
        @Bean
        public PasswordEncoder passwordEncoder() {

                return new BCryptPasswordEncoder();
        }

        // AUTHENTICATION MANAGER
        @Bean
        public AuthenticationManager authenticationManager(
                        AuthenticationConfiguration config)

                        throws Exception {

                return config.getAuthenticationManager();
        }

        // SECURITY FILTER CHAIN
        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http)

                        throws Exception {

                http

                                // DISABLE CSRF
                                // Already disabled globally — this covers webhook too,
                                // Razorpay's POST request doesn't carry a CSRF token anyway.
                                .csrf(csrf -> csrf.disable())

                                // ENABLE CORS
                                .cors(cors -> {
                                })

                                // STATELESS SESSION
                                .sessionManagement(session ->

                                session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))

                                // EXCEPTION HANDLING
                                .exceptionHandling(ex -> ex

                                                .authenticationEntryPoint(
                                                                authenticationEntryPoint)

                                                .accessDeniedHandler(
                                                                accessDeniedHandler))

                                // AUTHORIZATION
                                .authorizeHttpRequests(auth -> auth

                                                // PUBLIC APIs
                                                .requestMatchers(

                                                                "/api/auth/register",

                                                                "/api/auth/login",

                                                                "/api/auth/verify",

                                                                "/api/auth/resend-verification",

                                                                "/api/auth/forgot-password",

                                                                "/api/auth/verify-reset-otp",

                                                                "/api/auth/reset-password",

                                                                "/api/auth/resend-reset-otp",

                                                                "/api/auth/admin-login",

                                                                "/api/auth/verify-admin-login-otp",

                                                                "/api/auth/resend-admin-login-otp",

                                                                "/api/auth/refresh-token",

                                                                "/api/plans",

                                                                "/api/plans/**")

                                                .permitAll()

                                                // ✅ NEW: RAZORPAY WEBHOOK — must stay PUBLIC.
                                                // Razorpay calls this server-to-server, it has
                                                // NO JWT token, so it can never satisfy
                                                // hasAnyRole("USER","ADMIN") below.
                                                // Security here comes from signature
                                                // verification inside handleWebhook()
                                                // (Utils.verifyWebhookSignature using
                                                // RAZORPAY_WEBHOOK_SECRET) — NOT from auth.
                                                //
                                                // IMPORTANT: this matcher must be declared
                                                // BEFORE the general "/api/payment/**" rule
                                                // below. Spring Security evaluates matchers
                                                // top-to-bottom and uses the FIRST match —
                                                // if this line were after "/api/payment/**",
                                                // it would never be reached and webhook
                                                // calls would get blocked with 401/403.
                                                .requestMatchers(
                                                                "/api/payment/webhook")

                                                .permitAll()

                                                // ADMIN APIs
                                                .requestMatchers(
                                                                "/api/admin/**",
                                                                "/api/admin/plans/**")

                                                .hasRole("ADMIN")
                                                // Common api for payment,wallet,images
                                                // NOTE: /api/payment/webhook is already
                                                // carved out above, so it's unaffected by
                                                // this role restriction.
                                                .requestMatchers(

                                                                "/api/payment/**",

                                                                "/api/wallet/**",

                                                                "/api/images/**")

                                                .hasAnyRole("USER", "ADMIN"

                                                )

                                                // USER APIs
                                                .requestMatchers(

                                                                "/api/users/**")

                                                .hasRole("USER")

                                                // EVERYTHING ELSE
                                                .anyRequest()

                                                .authenticated())

                                // ADD JWT FILTER
                                .addFilterBefore(

                                                jwtFilter,

                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}