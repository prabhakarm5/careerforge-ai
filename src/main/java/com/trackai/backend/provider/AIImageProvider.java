package com.trackai.backend.provider;

import com.trackai.backend.dto.image.GenerateImageRequest;
import com.trackai.backend.dto.image.GenerateImageResponse;

public interface AIImageProvider {

    boolean supports(String modelId);

    GenerateImageResponse generateImage(GenerateImageRequest request);
}
