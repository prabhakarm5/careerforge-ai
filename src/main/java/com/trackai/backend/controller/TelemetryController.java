package com.trackai.backend.controller;

import com.trackai.backend.dto.admin.PageViewRequest;
import com.trackai.backend.service.AdminMonitoringService;
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

    @PostMapping("/page-view")
    public ResponseEntity<Void> pageView(
            @Valid @RequestBody PageViewRequest pageView,
            HttpServletRequest request) {
        monitoringService.recordPageView(pageView.path(), pageView.timezone(), pageView.locale(), request);
        return ResponseEntity.accepted().build();
    }
}
