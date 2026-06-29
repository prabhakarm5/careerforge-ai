package com.trackai.backend.provider;

import com.trackai.backend.dto.image.GenerateImageRequest;
import com.trackai.backend.dto.image.GenerateImageResponse;

public interface AIImageProvider {

    GenerateImageResponse generateImage(GenerateImageRequest request);
}
