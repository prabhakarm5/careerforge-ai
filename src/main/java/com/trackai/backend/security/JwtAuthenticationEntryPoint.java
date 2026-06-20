package com.trackai.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtAuthenticationEntryPoint

                implements AuthenticationEntryPoint {

        @Override
        public void commence(

                        HttpServletRequest request,

                        HttpServletResponse response,

                        AuthenticationException authException)

                        throws IOException, ServletException {

                response.setStatus(
                                HttpServletResponse.SC_UNAUTHORIZED);

                response.setContentType(
                                "application/json");

                Map<String, Object> error = new HashMap<>();

                error.put(
                                "timestamp",
                                LocalDateTime.now());

                error.put(
                                "status",
                                401);

                error.put(
                                "error",
                                "Unauthorized");

                error.put(
                                "message",
                                "Invalid or missing access token");

                error.put(
                                "path",
                                request.getRequestURI());

                new ObjectMapper()

                                .writeValue(
                                                response.getOutputStream(),
                                                error);
        }
}