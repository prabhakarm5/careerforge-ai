package com.trackai.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint
                implements AuthenticationEntryPoint {

        private final ObjectMapper objectMapper;

        @Override
        public void commence(

                        HttpServletRequest request,

                        HttpServletResponse response,

                        AuthenticationException authException)

                        throws IOException {

                response.setStatus(
                                HttpServletResponse.SC_UNAUTHORIZED);

                response.setContentType(
                                "application/json");

                Map<String, Object> error = new HashMap<>();

                error.put(
                                "timestamp",
                                LocalDateTime.now().toString());

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

                objectMapper.writeValue(
                                response.getOutputStream(),
                                error);
        }
}