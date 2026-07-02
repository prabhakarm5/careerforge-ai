package com.trackai.backend.dto.image;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class GenerateImageResponse {

    private String imageUrl;

    private byte[] imageBytes;

    private String storageUrl;

    private String providerImageId;

    private String prompt;

    @Builder.Default
    private String provider = "OPENROUTER";

}