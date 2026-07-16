package com.trackai.backend.controller;

import com.trackai.backend.dto.image.GenerateImageRequest;
import com.trackai.backend.dto.image.GenerateImageResponse;
import com.trackai.backend.dto.image.ImageHistoryResponse;
import com.trackai.backend.dto.image.ImageModelResponse;
import com.trackai.backend.service.ImageGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageGenerationController {

    private final ImageGenerationService imageGenerationService;

    @PostMapping(value = "/generate", consumes = "multipart/form-data")
    public ResponseEntity<GenerateImageResponse> generateMultipart(
            @Valid @ModelAttribute GenerateImageRequest request) {
        return ResponseEntity.ok(imageGenerationService.generateImage(request));
    }

    @PostMapping(value = "/generate", consumes = "application/json")
    public ResponseEntity<GenerateImageResponse> generateJson(
            @Valid @RequestBody GenerateImageRequest request) {
        return ResponseEntity.ok(imageGenerationService.generateImage(request));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ImageHistoryResponse>> history() {
        return ResponseEntity.ok(imageGenerationService.getHistory());
    }

    @GetMapping("/models")
    public ResponseEntity<List<ImageModelResponse>> models() {
        return ResponseEntity.ok(imageGenerationService.getModels());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Map<String, String>> download(@PathVariable String id) {
        return ResponseEntity.ok(imageGenerationService.download(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        imageGenerationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/favorite")
    public ResponseEntity<ImageHistoryResponse> favorite(@PathVariable String id) {
        return ResponseEntity.ok(imageGenerationService.toggleFavorite(id));
    }

    @PostMapping("/{id}/regenerate")
    public ResponseEntity<GenerateImageResponse> regenerate(@PathVariable String id) {
        return ResponseEntity.ok(imageGenerationService.regenerate(id));
    }
}