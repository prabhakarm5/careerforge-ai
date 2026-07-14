package com.trackai.backend.controller;

import com.trackai.backend.dto.ActionResponse;
import com.trackai.backend.dto.UpdateProfileRequest;
import com.trackai.backend.dto.UpdateProfileResponse;
import com.trackai.backend.dto.admin.AdminUserResponse;
import com.trackai.backend.dto.admin.AdminMessageRequest;
import com.trackai.backend.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/me")
    public ResponseEntity<AdminUserResponse> getCurrentAdmin() {
        return ResponseEntity.ok(AdminUserResponse.from(adminService.getCurrentAdmin()));
    }

    @PutMapping(value = "/me", consumes = "multipart/form-data")
    public ResponseEntity<UpdateProfileResponse> updateCurrentAdmin(
            @ModelAttribute @Valid UpdateProfileRequest request) {
        return ResponseEntity.ok(adminService.updateCurrentAdmin(request));
    }

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserResponse>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String q) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        PageRequest pageable = PageRequest.of(
                safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(adminService.getUsers(q, pageable));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(AdminUserResponse.from(adminService.getUserById(id)));
    }

    @PutMapping("/users/{id}/enable")
    public ResponseEntity<ActionResponse> enableUser(@PathVariable String id) {
        return ResponseEntity.ok(adminService.enableUser(id));
    }

    @PutMapping("/users/{id}/disable")
    public ResponseEntity<ActionResponse> disableUser(@PathVariable String id) {
        return ResponseEntity.ok(adminService.disableUser(id));
    }

    @PutMapping("/users/{id}/block")
    public ResponseEntity<ActionResponse> blockUser(@PathVariable String id) {
        return ResponseEntity.ok(adminService.blockUser(id));
    }

    @PutMapping("/users/{id}/unblock")
    public ResponseEntity<ActionResponse> unblockUser(@PathVariable String id) {
        return ResponseEntity.ok(adminService.unblockUser(id));
    }

    @PostMapping("/users/{id}/message")
    public ResponseEntity<ActionResponse> messageUser(
            @PathVariable String id,
            @Valid @RequestBody AdminMessageRequest request) {
        return ResponseEntity.accepted().body(adminService.sendMessage(id, request));
    }
    @DeleteMapping("/users/{id}")
    public ResponseEntity<ActionResponse> deleteUser(@PathVariable String id) {
        return ResponseEntity.ok(adminService.deleteUser(id));
    }
}