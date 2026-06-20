package com.trackai.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtAccessDeniedHandler

                implements AccessDeniedHandler {

        @Override
        public void handle(

                        HttpServletRequest request,

                        HttpServletResponse response,

                        AccessDeniedException accessDeniedException)

                        throws IOException, ServletException {

                // CONSOLE LOG
                System.out.println(

                                "ACCESS DENIED: "
                                                +

                                                request.getRequestURI());

                // RESPONSE STATUS
                response.setStatus(
                                HttpServletResponse.SC_FORBIDDEN);

                response.setContentType(
                                "application/json");

                // RESPONSE BODY
                Map<String, Object> body = new HashMap<>();

                body.put(
                                "timestamp",
                                LocalDateTime.now());

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

                // SEND JSON
                new ObjectMapper()

                                .writeValue(

                                                response.getOutputStream(),

                                                body);
        }
}