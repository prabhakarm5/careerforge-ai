package com.trackai.backend.controller;

import com.trackai.backend.dto.admin.PageViewRequest;
import com.trackai.backend.service.AdminMonitoringService;
import com.trackai.backend.service.RedisRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final AdminMonitoringService monitoringService;
    private final RedisRateLimitService rateLimitService;

    @PostMapping("/page-view")
    public ResponseEntity<Void> pageView(
            @Valid @RequestBody PageViewRequest pageView,
            HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String clientAddress = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",", 2)[0].trim();
        String clientKey = Integer.toHexString(String.valueOf(clientAddress).hashCode());

        // Public discovery pages must be measurable, but telemetry should never
        // become an unbounded anonymous write surface.
        var allowance = rateLimitService.allowRequest("telemetry:" + clientKey, 60, 60, 1);
        if (allowance.isAllowed()) {
            monitoringService.recordPageView(pageView.path(), pageView.timezone(), pageView.locale(), request);
        }
        return ResponseEntity.accepted().build();
    }
}
