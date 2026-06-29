package com.trackai.backend.dto.image;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class GenerateImageResponse {

    private String imageUrl;

    private String storageUrl;

    private String providerImageId;

    private String revisedPrompt;

    private String model;

    private Integer width;

    private Integer height;

    private String mimeType;

    @Builder.Default
    private String provider = "OPENROUTER";

}