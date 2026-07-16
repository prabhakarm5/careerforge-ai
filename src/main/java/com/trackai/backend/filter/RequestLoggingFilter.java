package com.trackai.backend.filter;

import com.trackai.backend.service.AdminMonitoringService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final AdminMonitoringService monitoringService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;

            // Request details stay at DEBUG; the admin view uses a bounded in-memory summary.
            log.debug(
                    "{} {} | Status={} | Time={}ms | IP={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration,
                    request.getRemoteAddr());

            if (shouldCapture(request, response)) {
                monitoringService.recordRequest(request, response.getStatus(), duration, response.getContentType(), responseSize(response));
            }
        }
    }

    private long responseSize(HttpServletResponse response) {
        String value = response.getHeader("Content-Length");
        if (value == null || value.isBlank()) return 0;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean shouldCapture(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getRequestURI();
        String contentType = response.getContentType();
        boolean streaming = contentType != null && contentType.startsWith("text/event-stream");
        return !streaming
                && !"OPTIONS".equalsIgnoreCase(request.getMethod())
                && !path.startsWith("/actuator")
                && !path.startsWith("/api/admin/monitoring")
                && !path.startsWith("/api/telemetry");
    }
}