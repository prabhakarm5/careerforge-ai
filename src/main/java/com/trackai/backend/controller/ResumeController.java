package com.trackai.backend.controller;

import com.trackai.backend.dto.resume.*;
import com.trackai.backend.service.ResumeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeProjectResponse> analyze(
            @RequestPart("resume") MultipartFile resume,
            @RequestParam(value = "jobDescription", required = false) String jobDescription,
            @RequestParam(value = "model", required = false)
            @Size(max = 100, message = "Model ID must be 100 characters or less") String model) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resumeService.analyze(resume, jobDescription, model));
    }

    @PostMapping("/{projectId}/job-match")
    public ResponseEntity<ResumeProjectResponse> matchJob(
            @PathVariable String projectId,
            @Valid @RequestBody JobDescriptionRequest request) {
        return ResponseEntity.ok(resumeService.matchJob(projectId, request));
    }

    @PostMapping("/{projectId}/chat")
    public ResponseEntity<ResumeChatResponse> chat(
            @PathVariable String projectId,
            @Valid @RequestBody ResumeChatRequest request) {
        return ResponseEntity.ok(resumeService.chat(projectId, request));
    }

    @PostMapping(path = "/{projectId}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> streamChat(
            @PathVariable String projectId,
            @Valid @RequestBody ResumeChatRequest request) {
        return Flux.defer(() -> resumeService.streamChat(projectId, request))
                .map(chunk -> ServerSentEvent.<Map<String, Object>>builder()
                        .event("chunk")
                        .data(Map.of("text", chunk))
                        .build())
                .concatWithValues(ServerSentEvent.<Map<String, Object>>builder()
                        .event("done")
                        .data(Map.of("projectId", projectId))
                        .build())
                .onErrorResume(error -> Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                        .event("error")
                        .data(Map.of(
                                "code", "RESUME_STREAM_ERROR",
                                "message", error.getMessage() == null
                                        ? "Resume Coach could not complete the response."
                                        : error.getMessage()))
                        .build()));
    }

    @PostMapping("/{projectId}/generate")
    public ResponseEntity<GeneratedResumeResponse> generate(
            @PathVariable String projectId,
            @Valid @RequestBody(required = false) GenerateResumeRequest request) {
        return ResponseEntity.ok(resumeService.generate(projectId, request));
    }

    @GetMapping
    public ResponseEntity<List<ResumeProjectSummaryResponse>> projects() {
        return ResponseEntity.ok(resumeService.getProjects());
    }

    @GetMapping("/models")
    public ResponseEntity<List<ResumeModelResponse>> models() {
        return ResponseEntity.ok(resumeService.getModels());
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ResumeProjectResponse> project(@PathVariable String projectId) {
        return ResponseEntity.ok(resumeService.getProject(projectId));
    }

    @GetMapping("/{projectId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String projectId) {
        ResumeService.ResumeDownload download = resumeService.download(projectId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.bytes());
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable String projectId) {
        resumeService.delete(projectId);
        return ResponseEntity.noContent().build();
    }
}
