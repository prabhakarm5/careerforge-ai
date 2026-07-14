package com.trackai.backend.controller;

import com.trackai.backend.dto.support.*;
import com.trackai.backend.service.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/support/tickets")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    @PostMapping
    public ResponseEntity<SupportTicketResponse> create(
            @Valid @RequestBody CreateSupportTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supportTicketService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<SupportTicketSummaryResponse>> mine() {
        return ResponseEntity.ok(supportTicketService.getMine());
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<SupportTicketResponse> detail(@PathVariable String ticketId) {
        return ResponseEntity.ok(supportTicketService.getMine(ticketId));
    }

    @PostMapping("/{ticketId}/messages")
    public ResponseEntity<SupportTicketResponse> reply(
            @PathVariable String ticketId,
            @Valid @RequestBody SupportReplyRequest request) {
        return ResponseEntity.ok(supportTicketService.replyAsUser(ticketId, request));
    }

    @PatchMapping("/{ticketId}/resolve")
    public ResponseEntity<SupportTicketResponse> resolve(@PathVariable String ticketId) {
        return ResponseEntity.ok(supportTicketService.resolveMine(ticketId));
    }

    @PatchMapping("/{ticketId}/reopen")
    public ResponseEntity<SupportTicketResponse> reopen(@PathVariable String ticketId) {
        return ResponseEntity.ok(supportTicketService.reopenMine(ticketId));
    }
}