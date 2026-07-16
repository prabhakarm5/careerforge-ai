package com.trackai.backend.controller;

import com.trackai.backend.dto.support.*;
import com.trackai.backend.enums.SupportTicketStatus;
import com.trackai.backend.service.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/support/tickets")
@RequiredArgsConstructor
public class AdminSupportTicketController {

    private final SupportTicketService supportTicketService;

    @GetMapping
    public ResponseEntity<List<SupportTicketSummaryResponse>> tickets(
            @RequestParam(required = false) SupportTicketStatus status) {
        return ResponseEntity.ok(supportTicketService.getAll(status));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<SupportTicketResponse> detail(@PathVariable String ticketId) {
        return ResponseEntity.ok(supportTicketService.getAsAdmin(ticketId));
    }

    @PostMapping("/{ticketId}/messages")
    public ResponseEntity<SupportTicketResponse> reply(
            @PathVariable String ticketId,
            @Valid @RequestBody SupportReplyRequest request) {
        return ResponseEntity.ok(supportTicketService.replyAsAdmin(ticketId, request));
    }

    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Void> delete(@PathVariable String ticketId) {
        supportTicketService.deleteAsAdmin(ticketId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{ticketId}/status")
    public ResponseEntity<SupportTicketResponse> updateStatus(
            @PathVariable String ticketId,
            @Valid @RequestBody SupportStatusRequest request) {
        return ResponseEntity.ok(supportTicketService.updateStatusAsAdmin(ticketId, request));
    }
}