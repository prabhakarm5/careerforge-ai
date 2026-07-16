package com.trackai.backend.service;

import com.trackai.backend.dto.image.GenerateImageRequest;
import com.trackai.backend.dto.image.GenerateImageResponse;
import com.trackai.backend.dto.image.ImageHistoryResponse;
import com.trackai.backend.dto.image.ImageModelResponse;

import java.util.List;
import java.util.Map;

public interface ImageGenerationService {
    GenerateImageResponse generateImage(GenerateImageRequest request);
    List<ImageHistoryResponse> getHistory();
    List<ImageModelResponse> getModels();
    void delete(String imageId);
    ImageHistoryResponse toggleFavorite(String imageId);
    Map<String, String> download(String imageId);
    GenerateImageResponse regenerate(String imageId);
}