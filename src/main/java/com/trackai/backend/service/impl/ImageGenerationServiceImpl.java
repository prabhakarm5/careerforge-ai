package com.trackai.backend.service.impl;

import com.trackai.backend.config.HuggingFaceImageProperties;
import com.trackai.backend.config.OpenRouterProperties;
import com.trackai.backend.config.TokenProperties;
import com.trackai.backend.dto.cloudinary.CloudinaryUploadResponse;
import com.trackai.backend.dto.image.GenerateImageRequest;
import com.trackai.backend.dto.image.GenerateImageResponse;
import com.trackai.backend.dto.image.ImageHistoryResponse;
import com.trackai.backend.dto.image.ImageModelResponse;
import com.trackai.backend.entity.GeneratedImage;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.ImageStatus;
import com.trackai.backend.exception.ImageNotFoundException;
import com.trackai.backend.provider.AIImageProvider;
import com.trackai.backend.repository.GeneratedImageRepository;
import com.trackai.backend.service.CloudinaryService;
import com.trackai.backend.service.ImageDownloaderService;
import com.trackai.backend.service.ImageGenerationService;
import com.trackai.backend.service.UserService;
import com.trackai.backend.service.WalletService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImageGenerationServiceImpl implements ImageGenerationService {

    private static final int HISTORY_LIMIT = 100;

    private final WalletService walletService;
    private final List<AIImageProvider> imageProviders;
    private final GeneratedImageRepository repository;
    private final UserService userService;
    private final ImageDownloaderService imageDownloaderService;
    private final CloudinaryService cloudinaryService;
    private final TokenProperties tokenProperties;
    private final OpenRouterProperties openRouterProperties;
    private final HuggingFaceImageProperties huggingFaceImageProperties;

    @Override
    @Transactional
    public GenerateImageResponse generateImage(GenerateImageRequest request) {
        walletService.checkImageGenerationTokens();
        User user = userService.getCurrentUser();

        AIImageProvider imageProvider = imageProviders.stream()
                .filter(provider -> provider.supports(request.getModel()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Selected image model is not available."));
        GenerateImageResponse providerResponse = imageProvider.generateImage(request);

        byte[] imageBytes = providerResponse.getImageBytes() != null
                ? providerResponse.getImageBytes()
                : imageDownloaderService.downloadImage(providerResponse.getImageUrl());
        if (imageBytes == null || imageBytes.length == 0) {
            throw new RuntimeException("The image provider returned an empty image.");
        }

        CloudinaryUploadResponse upload = cloudinaryService.uploadGeneratedImage(imageBytes);
        String selectedModel = providerResponse.getModelId() != null
                ? providerResponse.getModelId()
                : request.getModel();

        GeneratedImage saved = repository.save(GeneratedImage.builder()
                .prompt(request.getPrompt().trim())
                // Always expose the stable Cloudinary URL; provider CDN URLs may expire.
                .imageUrl(upload.getSecureUrl())
                .storageUrl(upload.getSecureUrl())
                .provider(providerResponse.getProvider())
                .modelId(selectedModel)
                .providerImageId(providerResponse.getProviderImageId())
                .cloudinaryPublicId(upload.getPublicId())
                .tokensUsed(tokenProperties.getImage())
                .status(ImageStatus.COMPLETED.name())
                .favorite(false)
                .user(user)
                .build());

        walletService.consumeImageTokens(tokenProperties.getImage());
        return toGenerateResponse(saved);
    }

    @Override
    public List<ImageHistoryResponse> getHistory() {
        String userId = userService.getCurrentUser().getId();
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, HISTORY_LIMIT))
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Override
    public List<ImageModelResponse> getModels() {
        List<ImageModelResponse> models = new ArrayList<>();
        if (huggingFaceImageProperties.isEnabled()) {
            for (HuggingFaceImageProperties.ModelInfo model : huggingFaceImageProperties.availableModels()) {
                models.add(ImageModelResponse.builder()
                        .id("huggingface:" + model.getId())
                        .label(model.getLabel())
                        .description(model.getDescription())
                        .provider("HUGGING_FACE")
                        .supportsImageInput(model.isSupportsImageInput())
                        .requiresImageInput(model.isRequiresImageInput())
                        .defaultModel(model.isDefaultModel())
                        .accessLabel("HF free credits")
                        .build());
            }
        }
        for (OpenRouterProperties.ModelInfo model : openRouterProperties.getImageModels()) {
            models.add(ImageModelResponse.builder()
                    .id(model.getId())
                    .label(model.getLabel())
                    .description(model.getDescription())
                    .provider("OPENROUTER")
                    .supportsImageInput(true)
                    .requiresImageInput(false)
                    .defaultModel(models.isEmpty())
                    .accessLabel("Premium")
                    .build());
        }
        return models;
    }

    @Override
    @Transactional
    public void delete(String imageId) {
        String userId = userService.getCurrentUser().getId();
        GeneratedImage image = ownedImage(imageId, userId);

        if (image.getCloudinaryPublicId() != null && !image.getCloudinaryPublicId().isBlank()) {
            try {
                cloudinaryService.deleteImage(image.getCloudinaryPublicId());
            } catch (RuntimeException ignored) {
                // The database record must still be removable when remote cleanup is unavailable.
            }
        }
        repository.delete(image);
        repository.flush();
    }

    @Override
    @Transactional
    public ImageHistoryResponse toggleFavorite(String imageId) {
        String userId = userService.getCurrentUser().getId();
        GeneratedImage image = ownedImage(imageId, userId);
        image.setFavorite(!Boolean.TRUE.equals(image.getFavorite()));
        return toHistoryResponse(repository.save(image));
    }

    @Override
    @Transactional
    public GenerateImageResponse regenerate(String imageId) {
        String userId = userService.getCurrentUser().getId();
        GeneratedImage image = ownedImage(imageId, userId);
        GenerateImageRequest request = new GenerateImageRequest();
        request.setPrompt(image.getPrompt());
        request.setModel(image.getModelId());
        return generateImage(request);
    }

    @Override
    public Map<String, String> download(String imageId) {
        String userId = userService.getCurrentUser().getId();
        GeneratedImage image = ownedImage(imageId, userId);
        String url = image.getStorageUrl() == null || image.getStorageUrl().isBlank()
                ? image.getImageUrl()
                : image.getStorageUrl();
        return Map.of("downloadUrl", url);
    }

    private GeneratedImage ownedImage(String imageId, String userId) {
        if (imageId == null || imageId.isBlank() || "undefined".equalsIgnoreCase(imageId)) {
            throw new ImageNotFoundException("Image id is missing. Refresh history and try again.");
        }
        return repository.findByIdAndUserId(imageId, userId)
                .orElseThrow(() -> new ImageNotFoundException("Image not found."));
    }

    private ImageHistoryResponse toHistoryResponse(GeneratedImage image) {
        return ImageHistoryResponse.builder()
                .id(image.getId())
                .prompt(image.getPrompt())
                .imageUrl(image.getImageUrl())
                .storageUrl(image.getStorageUrl())
                .provider(image.getProvider())
                .modelId(image.getModelId())
                .status(image.getStatus())
                .tokensUsed(image.getTokensUsed())
                .favorite(image.getFavorite())
                .createdAt(image.getCreatedAt())
                .build();
    }

    private GenerateImageResponse toGenerateResponse(GeneratedImage image) {
        return GenerateImageResponse.builder()
                .id(image.getId())
                .prompt(image.getPrompt())
                .imageUrl(image.getImageUrl())
                .storageUrl(image.getStorageUrl())
                .provider(image.getProvider())
                .modelId(image.getModelId())
                .providerImageId(image.getProviderImageId())
                .tokensUsed(image.getTokensUsed())
                .favorite(image.getFavorite())
                .status(image.getStatus())
                .createdAt(image.getCreatedAt())
                // Raw provider bytes are uploaded once and never echoed as a huge base64 JSON field.
                .imageBytes(null)
                .build();
    }
}