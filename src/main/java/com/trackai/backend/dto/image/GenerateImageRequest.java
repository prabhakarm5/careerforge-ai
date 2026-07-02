package com.trackai.backend.dto.image;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class GenerateImageRequest {

    @NotBlank(message = "Prompt is required")
    private String prompt;

    // Optional OpenRouter image model id from /api/images/models.
    private String model;

    // Optional
    private MultipartFile image;

}
