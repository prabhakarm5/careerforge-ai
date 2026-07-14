package com.trackai.backend.controller;

import com.trackai.backend.dto.admin.AdminSystemStatusResponse;
import com.trackai.backend.service.AdminSystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
public class AdminSystemController {

    private final AdminSystemService adminSystemService;

    @GetMapping("/status")
    public ResponseEntity<AdminSystemStatusResponse> status() {
        return ResponseEntity.ok(adminSystemService.getStatus());
    }
}