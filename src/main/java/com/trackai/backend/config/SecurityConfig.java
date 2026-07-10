package com.trackai.backend.config;

import com.trackai.backend.security.JwtAccessDeniedHandler;
import com.trackai.backend.security.JwtAuthenticationEntryPoint;
import com.trackai.backend.security.JwtFilter;
import com.trackai.backend.security.OAuth2LoginFailureHandler;
import com.trackai.backend.security.OAuth2LoginSuccessHandler;
import com.trackai.backend.security.OAuth2UserInfoService;

import jakarta.servlet.DispatcherType;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Production-grade Spring Security configuration for TrackAI backend.
 *
 * Design notes:
 * - Stateless JWT auth (no sessions, no CSRF token needed).
 * - CSRF disabled deliberately: safe ONLY because we don't use cookie-based
 * session auth. If cookie-based auth is ever added, CSRF must be re-enabled.
 * - Form login / HTTP Basic disabled: this is a pure REST API, browser login
 * forms and basic-auth popups have no place here and are an attack surface
 * if left on by default.
 * - Security headers hardened for defense-in-depth (clickjacking, MIME
 * sniffing, transport security).
 * - Route authorization ordered carefully: MORE SPECIFIC matchers (webhook)
 * must come before broader matchers ("/api/payment/**") because Spring
 * Security's AuthorizationManager evaluates rules top-to-bottom and stops
 * at the first match.
 *
 * FIX â€” ASYNC DISPATCH CRASH (root cause of the AccessDeniedException /
 * "response already committed" errors you were seeing right when an SSE
 * chat stream finished):
 *
 * SseEmitter.complete() triggers Tomcat to run an ASYNC dispatch back
 * through the servlet/filter chain so the container can cleanly finalize
 * the response. JwtFilter correctly skips itself on that dispatch
 * (shouldNotFilterAsyncDispatch() -> true) â€” but Spring Security's OWN
 * built-in AuthorizationFilter does NOT skip itself by default. It runs
 * again on that async dispatch, finds no SecurityContext (nothing set it
 * on this new dispatch, since JwtFilter deliberately didn't run), and
 * throws AccessDeniedException on a response that's already been fully
 * streamed and committed to the client â€” which Spring then can't even
 * turn into a clean error response, so the connection gets abruptly
 * reset instead of closing cleanly. That's exactly what corrupts the
 * end of a stream, breaks "done"/save events, and makes memory/history
 * look inconsistent afterwards.
 *
 * The single-line fix: explicitly permitAll() on DispatcherType.ASYNC
 * and DispatcherType.ERROR, placed as the FIRST authorization rule (must
 * come before every other matcher, since Spring Security evaluates rules
 * top-to-bottom and stops at the first match). This does not weaken
 * security â€” it does not skip authentication for the original incoming
 * request (that still goes through JwtFilter + the
 * "anyRequest().authenticated()"
 * rule below on the INITIAL dispatch). It only stops Spring from
 * re-authorizing the container's own internal completion dispatch of a
 * request that was already authenticated once.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtFilter jwtFilter;
        private final JwtAuthenticationEntryPoint authenticationEntryPoint;
        private final JwtAccessDeniedHandler accessDeniedHandler;
        private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
        private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
        private final OAuth2UserInfoService oAuth2UserInfoService;

        @Bean
        public AuthenticationManager authenticationManager(
                        AuthenticationConfiguration config) throws Exception {
                return config.getAuthenticationManager();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

                http

                                // â”€â”€ CSRF â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                                // Disabled: we are a stateless JWT API, not browser-session
                                // based. CSRF tokens only matter when the browser auto-attaches
                                // credentials (cookies). Razorpay's webhook POST also has no
                                // CSRF token, so this must stay disabled for that call to work.
                                .csrf(AbstractHttpConfigurer::disable)

                                // â”€â”€ CORS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                                // Delegates to the CorsConfigurationSource bean (CorsConfig
                                // class) â€” allowed origins/methods are defined there, NOT
                                // here, so origin changes never require touching this file.
                                .cors(cors -> {
                                })

                                // â”€â”€ SESSIONS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                                // JWT still protects API calls after login. OAuth2 briefly needs server-side state during provider redirect. Every API request must carry
                                // a valid JWT. Required for horizontal scaling (multiple
                                // Elastic Beanstalk instances, no sticky sessions needed).
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                                // â”€â”€ AUTH ERROR HANDLING â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                                // Custom entry point / access-denied handler so we return
                                // clean JSON (401/403) instead of Spring's default HTML
                                // error pages or redirect-to-login behavior.
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint(authenticationEntryPoint)
                                                .accessDeniedHandler(accessDeniedHandler))

                                // â”€â”€ FORM LOGIN / HTTP BASIC â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                                // Explicitly disabled. Spring Security auto-configures a
                                // default login page and basic-auth prompt if left enabled â€”
                                // both are dead weight and a minor attack surface on a
                                // pure JSON REST API that only authenticates via JWT.
                                .formLogin(AbstractHttpConfigurer::disable)
                                .httpBasic(AbstractHttpConfigurer::disable)

                                // â”€â”€ SECURITY HEADERS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                                .headers(headers -> headers
                                                // Prevent this API's responses from being framed by
                                                // another origin (clickjacking protection). SAMEORIGIN
                                                // is enough since we don't need cross-origin framing.
                                                .frameOptions(frame -> frame.sameOrigin())

                                                // Adds X-Content-Type-Options: nosniff â€” stops
                                                // browsers from MIME-sniffing responses into an
                                                // executable type they weren't served as.
                                                .contentTypeOptions(contentType -> {
                                                })

                                                // X-XSS-Protection is deprecated in modern browsers
                                                // and can itself introduce vulnerabilities in old
                                                // ones (e.g. IE). We disable it explicitly and rely
                                                // on a future Content-Security-Policy instead.
                                                .xssProtection(xss -> xss.disable())

                                                // HSTS: forces browsers to only ever talk HTTPS to
                                                // this domain for 1 year (31536000s), including
                                                // subdomains. Spring only actually sends this header
                                                // on responses that were served over HTTPS, so it's
                                                // safe to leave on even while still behind the plain
                                                // HTTP Elastic Beanstalk default URL â€” it becomes
                                                // fully active the moment ACM/HTTPS + custom domain
                                                // are added, with zero code changes needed then.
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000)))

                                // â”€â”€ AUTHORIZATION RULES â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                                .authorizeHttpRequests(auth -> auth

                                                // â¬‡ï¸ MUST BE THE VERY FIRST RULE. Spring Security
                                                // evaluates matchers top-to-bottom and stops at the
                                                // first match, so this has to win before any other
                                                // rule gets a chance to run "anyRequest().authenticated()"
                                                // against the container's internal ASYNC/ERROR
                                                // dispatch of an SSE stream that already finished.
                                                // See the class-level Javadoc above for the full
                                                // explanation â€” this is the actual fix for the
                                                // AccessDeniedException / "response already
                                                // committed" crash at the end of every chat stream.
                                                .dispatcherTypeMatchers(
                                                                DispatcherType.ASYNC,
                                                                DispatcherType.ERROR)
                                                .permitAll()

                                                // Public auth endpoints â€” no token exists yet at
                                                // login/register/otp/refresh time, so these must
                                                // stay open.
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
                                                                "/api/auth/oauth-session",
                                                                "/oauth2/**",
                                                                "/login/oauth2/**",
                                                                "/api/plans",
                                                                "/api/plans/**")
                                                .permitAll()

                                                // Elastic Beanstalk hits this endpoint (unauthenticated)
                                                // to determine instance health â€” must be public or
                                                // EB will mark healthy instances as down.
                                                .requestMatchers("/actuator/health").permitAll()

                                                // API docs â€” harmless to expose publicly, useful for
                                                // frontend/mobile devs integrating against the API.
                                                // Remove/lock these down if the API is not meant to
                                                // be publicly discoverable in production.
                                                .requestMatchers(
                                                                "/swagger-ui/**",
                                                                "/v3/api-docs/**")
                                                .permitAll()

                                                // RAZORPAY WEBHOOK â€” must stay PUBLIC.
                                                // Razorpay calls this server-to-server with NO JWT,
                                                // so it can never satisfy hasAnyRole("USER","ADMIN")
                                                // below. Security here comes entirely from HMAC
                                                // signature verification inside handleWebhook()
                                                // (Utils.verifyWebhookSignature using
                                                // RAZORPAY_WEBHOOK_SECRET) â€” NOT from Spring auth.
                                                //
                                                // ORDER MATTERS: this matcher MUST be declared BEFORE
                                                // the broader "/api/payment/**" rule below. Spring
                                                // Security evaluates matchers top-to-bottom and stops
                                                // at the first match â€” if this line were moved after
                                                // "/api/payment/**", the webhook would fall under the
                                                // authenticated rule instead and every Razorpay
                                                // callback would get rejected with 401/403.
                                                .requestMatchers("/api/payment/webhook").permitAll()

                                                // Admin-only surface.
                                                .requestMatchers(
                                                                "/api/admin/**",
                                                                "/api/admin/plans/**")
                                                .hasRole("ADMIN")

                                                // Shared USER + ADMIN surface.
                                                // NOTE: /api/payment/webhook is already carved out
                                                // above, so this role restriction does not affect it.
                                                .requestMatchers(
                                                                "/api/payment/**",
                                                                "/api/wallet/**",
                                                                "/api/images/**")
                                                .hasAnyRole("USER", "ADMIN")

                                                // User-only surface.
                                                .requestMatchers("/api/users/**").hasRole("USER")

                                                // Default-deny: anything not explicitly listed above
                                                // requires a valid authenticated JWT. This is the
                                                // safety net â€” new endpoints are secure-by-default
                                                // unless someone deliberately opens them up.
                                                .anyRequest().authenticated())

                                // OAuth2 success creates the same JWT cookie session as password login.
                                .oauth2Login(oauth2 -> oauth2
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(oAuth2UserInfoService))
                                                .successHandler(oAuth2LoginSuccessHandler)
                                                .failureHandler(oAuth2LoginFailureHandler))

                                // JWT filter runs before Spring's own username/password
                                // filter so requests are authenticated from the token before
                                // Spring Security's default filter chain even looks at them.
                                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}