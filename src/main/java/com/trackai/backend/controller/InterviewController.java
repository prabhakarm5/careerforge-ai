package com.trackai.backend.controller;

import com.trackai.backend.dto.interview.*;
import com.trackai.backend.service.InterviewService;
import com.trackai.backend.service.InterviewLiveTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {
    private final InterviewService interviewService;
    private final InterviewLiveTokenService interviewLiveTokenService;

    @PostMapping("/live-token")
    public ResponseEntity<LiveInterviewTokenResponse> liveToken(
            @Valid @RequestBody LiveInterviewTokenRequest request) {
        return ResponseEntity.ok(interviewLiveTokenService.create(request));
    }

    @PostMapping
    public ResponseEntity<InterviewSessionResponse> start(@Valid @RequestBody StartInterviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(interviewService.start(request));
    }

    @PostMapping("/{sessionId}/answers")
    public ResponseEntity<InterviewSessionResponse> answer(
            @PathVariable String sessionId,
            @Valid @RequestBody InterviewAnswerRequest request) {
        return ResponseEntity.ok(interviewService.answer(sessionId, request));
    }

    @GetMapping
    public ResponseEntity<List<InterviewSummaryResponse>> history() {
        return ResponseEntity.ok(interviewService.history());
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<InterviewSessionResponse> get(@PathVariable String sessionId) {
        return ResponseEntity.ok(interviewService.get(sessionId));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> delete(@PathVariable String sessionId) {
        interviewService.delete(sessionId);
        return ResponseEntity.noContent().build();
    }
}
