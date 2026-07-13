package com.trackai.backend.controller;

import com.trackai.backend.dto.admin.PromoCodeRequest;
import com.trackai.backend.dto.admin.PromoCodeResponse;
import com.trackai.backend.dto.promo.PromoClaimResponse;
import com.trackai.backend.service.PromoCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PromoCodeController {
    private final PromoCodeService service;

    @GetMapping("/api/promos")
    public ResponseEntity<List<PromoCodeResponse>> available() {
        return ResponseEntity.ok(service.getAvailable());
    }

    @PostMapping("/api/promos/{code}/claim")
    public ResponseEntity<PromoClaimResponse> claim(@PathVariable String code) {
        return ResponseEntity.ok(service.claim(code));
    }

    @GetMapping("/api/admin/promos")
    public ResponseEntity<List<PromoCodeResponse>> all() {
        return ResponseEntity.ok(service.getAll());
    }

    @PostMapping("/api/admin/promos")
    public ResponseEntity<PromoCodeResponse> create(@Valid @RequestBody PromoCodeRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/api/admin/promos/{id}")
    public ResponseEntity<PromoCodeResponse> update(
            @PathVariable String id, @Valid @RequestBody PromoCodeRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/api/admin/promos/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Promo code deleted successfully"));
    }
}