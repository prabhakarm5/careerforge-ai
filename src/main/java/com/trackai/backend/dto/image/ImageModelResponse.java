package com.trackai.backend.dto.image;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImageModelResponse {
    private String id;
    private String label;
    private String description;
    private String provider;
    private boolean supportsImageInput;
    private boolean requiresImageInput;
    private boolean defaultModel;
    private String accessLabel;
}