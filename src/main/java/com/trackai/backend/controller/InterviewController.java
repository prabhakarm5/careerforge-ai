package com.trackai.backend.controller;

import com.trackai.backend.dto.interview.*;
import com.trackai.backend.service.InterviewService;
import com.trackai.backend.service.InterviewLiveTokenService;
import com.trackai.backend.service.InterviewContextExtractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {
    private final InterviewService interviewService;
    private final InterviewLiveTokenService interviewLiveTokenService;
    private final InterviewContextExtractionService contextExtractionService;

    @PostMapping(path = "/context/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InterviewContextExtractionResponse> extractContext(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "model", required = false) String model,
            @RequestPart(value = "contextType", required = false) String contextType) {
        return ResponseEntity.ok(contextExtractionService.extract(file, model, contextType));
    }

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
