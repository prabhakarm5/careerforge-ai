package com.trackai.backend.controller;

import com.trackai.backend.config.OpenRouterProperties;
import com.trackai.backend.dto.image.GenerateImageRequest;
import com.trackai.backend.dto.image.GenerateImageResponse;
import com.trackai.backend.dto.image.ImageHistoryResponse;
import com.trackai.backend.service.ImageGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageGenerationController {

    private final ImageGenerationService imageGenerationService;
    private final OpenRouterProperties openRouterProperties;

    /*
     * OLD CODE:
     *
     * @PostMapping(value = "/generate", consumes = "multipart/form-data")
     * public ResponseEntity<GenerateImageResponse> generate(
     *         @Valid @ModelAttribute GenerateImageRequest request) {
     *
     *     return ResponseEntity.ok(imageGenerationService.generateImage(request));
     * }
     */

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

        return ResponseEntity.ok(

                imageGenerationService
                        .getHistory());

    }

    @GetMapping("/models")
    public ResponseEntity<List<OpenRouterProperties.ModelInfo>> models() {
        return ResponseEntity.ok(openRouterProperties.getImageModels());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Map<String, String>> download(

            @PathVariable String id) {

        return ResponseEntity.ok(

                imageGenerationService

                        .download(id)

        );

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id) {

        imageGenerationService.delete(id);

        return ResponseEntity.noContent().build();

    }

    @PatchMapping("/{id}/favorite")
    public ResponseEntity<Void> favorite(

            @PathVariable String id) {

        imageGenerationService.toggleFavorite(id);

        return ResponseEntity.ok().build();

    }

    @PostMapping("/{id}/regenerate")
    public ResponseEntity<GenerateImageResponse> regenerate(

            @PathVariable String id) {

        return ResponseEntity.ok(

                imageGenerationService

                        .regenerate(id)

        );

    }

}
