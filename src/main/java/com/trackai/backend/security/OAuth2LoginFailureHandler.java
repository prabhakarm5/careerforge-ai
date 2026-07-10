package com.trackai.backend.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

        private final CookieUtil cookieUtil;

        @Value("${app.frontend-url}")
        private String frontendUrl;

        @Override
        public void onAuthenticationFailure(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        AuthenticationException exception) throws IOException, ServletException {

                cookieUtil.clearAllAuthCookies(response);

                String message = exception.getMessage() == null || exception.getMessage().isBlank()
                                ? "Social login was cancelled or denied. Please try again."
                                : exception.getMessage();

                String errorUrl = UriComponentsBuilder
                                .fromUriString(frontendUrl)
                                .path("/login")
                                .queryParam("oauthError", message)
                                .build()
                                .toUriString();

                response.sendRedirect(errorUrl);
        }
}