package com.trackai.backend.dto.image;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class GenerateImageResponse {
    private String id;
    private String imageUrl;
    private byte[] imageBytes;
    private String storageUrl;
    private String providerImageId;
    private String prompt;
    private String modelId;
    private Long tokensUsed;
    private Boolean favorite;
    private String status;
    private LocalDateTime createdAt;

    @Builder.Default
    private String provider = "OPENROUTER";
}