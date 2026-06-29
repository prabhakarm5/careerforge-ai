package com.trackai.backend.dto.image;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenerateImageRequest {

    @NotBlank(message = "Prompt is required")
    private String prompt;

    private String negativePrompt;

    // square | portrait | landscape
    private String aspectRatio = "1:1";

    private String model;
}