package com.trackai.backend.controller;

import com.trackai.backend.dto.coverletter.*;
import com.trackai.backend.service.CoverLetterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/cover-letters")
@RequiredArgsConstructor
public class CoverLetterController {

    private final CoverLetterService coverLetterService;

    @PostMapping
    public ResponseEntity<CoverLetterResponse> generate(
            @Valid @RequestBody GenerateCoverLetterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(coverLetterService.generate(request));
    }

    @PostMapping("/{id}/regenerate")
    public ResponseEntity<CoverLetterResponse> regenerate(
            @PathVariable String id,
            @Valid @RequestBody(required = false) RegenerateCoverLetterRequest request) {
        return ResponseEntity.ok(coverLetterService.regenerate(id,
                request == null ? new RegenerateCoverLetterRequest() : request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CoverLetterResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateCoverLetterRequest request) {
        return ResponseEntity.ok(coverLetterService.update(id, request));
    }

    @GetMapping
    public ResponseEntity<List<CoverLetterSummaryResponse>> history() {
        return ResponseEntity.ok(coverLetterService.getHistory());
    }

    @GetMapping("/styles")
    public ResponseEntity<List<CoverLetterStyleResponse>> styles() {
        return ResponseEntity.ok(coverLetterService.getStyles());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CoverLetterResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(coverLetterService.get(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable String id,
            @RequestParam(defaultValue = "pdf") String format) {
        CoverLetterService.DocumentDownload download = coverLetterService.download(id, format);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.bytes());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        coverLetterService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
