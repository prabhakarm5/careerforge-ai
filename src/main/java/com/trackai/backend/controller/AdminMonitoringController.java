package com.trackai.backend.controller;

import com.trackai.backend.dto.admin.AdminMonitoringResponse;
import com.trackai.backend.dto.admin.AdminUserActivityResponse;
import com.trackai.backend.service.AdminMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/monitoring")
@RequiredArgsConstructor
public class AdminMonitoringController {

    private final AdminMonitoringService monitoringService;

    @GetMapping("/overview")
    public ResponseEntity<AdminMonitoringResponse> overview() {
        return ResponseEntity.ok(monitoringService.overview());
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserActivityResponse> userActivity(@PathVariable String userId) {
        return ResponseEntity.ok(monitoringService.activityForUser(userId));
    }
}