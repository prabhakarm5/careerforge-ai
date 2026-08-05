package com.trackai.backend.service;

import com.trackai.backend.dto.image.GenerateImageRequest;
import com.trackai.backend.dto.image.GenerateImageResponse;
import com.trackai.backend.dto.image.ImageHistoryResponse;
import com.trackai.backend.dto.image.ImageModelResponse;

import java.util.List;

public interface ImageGenerationService {
    GenerateImageResponse generateImage(GenerateImageRequest request);
    List<ImageHistoryResponse> getHistory();
    List<ImageModelResponse> getModels();
    void delete(String imageId);
    ImageHistoryResponse toggleFavorite(String imageId);
    ImageDownload download(String imageId);

    record ImageDownload(byte[] bytes, String contentType, String fileName) {}
    GenerateImageResponse regenerate(String imageId);
}