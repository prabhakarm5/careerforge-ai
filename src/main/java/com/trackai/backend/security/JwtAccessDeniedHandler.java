package com.trackai.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler
                implements AccessDeniedHandler {

        private final ObjectMapper objectMapper;

        @Override
        public void handle(

                        HttpServletRequest request,

                        HttpServletResponse response,

                        AccessDeniedException accessDeniedException)

                        throws IOException {

                response.setStatus(
                                HttpServletResponse.SC_FORBIDDEN);

                response.setContentType(
                                "application/json");

                Map<String, Object> body = new HashMap<>();

                body.put(
                                "timestamp",
                                LocalDateTime.now().toString());

                body.put(
                                "status",
                                403);

                body.put(
                                "error",
                                "Forbidden");

                body.put(
                                "message",
                                "Access denied");

                body.put(
                                "path",
                                request.getRequestURI());

                objectMapper.writeValue(
                                response.getOutputStream(),
                                body);
        }
}