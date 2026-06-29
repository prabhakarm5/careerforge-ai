package com.trackai.backend.service.impl;

import com.trackai.backend.config.TokenProperties;
import com.trackai.backend.dto.cloudinary.CloudinaryUploadResponse;
import com.trackai.backend.dto.image.GenerateImageRequest;
import com.trackai.backend.dto.image.GenerateImageResponse;
import com.trackai.backend.dto.image.ImageHistoryResponse;
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
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImageGenerationServiceImpl
                implements ImageGenerationService {

        private final WalletService walletService;

        private final AIImageProvider imageProvider;

        private final GeneratedImageRepository repository;

        private final UserService userService;

        private final ImageDownloaderService imageDownloaderService;

        private final CloudinaryService cloudinaryService;

        private final TokenProperties tokenProperties;

        @Transactional
        @Override
        public GenerateImageResponse generateImage(
                        GenerateImageRequest request) {

                // 1. Check Wallet
                walletService.checkImageGenerationTokens();

                // 2. Generate Image from OpenRouter
                GenerateImageResponse response = imageProvider.generateImage(request);

                // 3. Download Image
                byte[] imageBytes = imageDownloaderService.downloadImage(
                                response.getImageUrl());

                // 4. Upload to Cloudinary
                CloudinaryUploadResponse upload =

                                cloudinaryService.uploadGeneratedImage(

                                                imageBytes

                                );

                // 5. Current User
                User user = userService.getCurrentUser();

                // 6. Save Database
                GeneratedImage image = GeneratedImage.builder()

                                .prompt(request.getPrompt())

                                .negativePrompt(
                                                request.getNegativePrompt())

                                .aspectRatio(
                                                request.getAspectRatio())

                                .model(
                                                response.getModel())

                                .imageUrl(
                                                response.getImageUrl())

                                .storageUrl(
                                                upload.getSecureUrl())

                                .cloudinaryPublicId(
                                                upload.getPublicId())

                                .width(
                                                upload.getWidth())

                                .height(
                                                upload.getHeight())

                                .imageSize(
                                                upload.getBytes())

                                .mimeType(
                                                upload.getFormat())

                                .provider(
                                                response.getProvider())

                                .status(
                                                ImageStatus.COMPLETED.name())

                                .user(user)

                                .favorite(false)

                                .build();

                repository.save(image);

                // 7. Deduct Wallet
                walletService.consumeImageTokens(
                                tokenProperties.getImage());

                // 8. Return Cloudinary URL
                response.setStorageUrl(

                                upload.getSecureUrl()

                );

                return response;
        }

        @Override
        public List<ImageHistoryResponse> getHistory() {

                User user = userService.getCurrentUser();

                return repository

                                .findByUserOrderByCreatedAtDesc(user)

                                .stream()

                                .map(image ->

                                ImageHistoryResponse.builder()

                                                .id(image.getId())

                                                .prompt(image.getPrompt())

                                                .imageUrl(image.getStorageUrl())

                                                .storageUrl(image.getStorageUrl())

                                                .aspectRatio(image.getAspectRatio())

                                                .model(image.getModel())

                                                .width(image.getWidth())

                                                .height(image.getHeight())

                                                .favorite(image.getFavorite())

                                                .createdAt(image.getCreatedAt())

                                                .build()

                                )

                                .toList();

        }

        @Override
        @Transactional
        public void delete(String imageId) {

                User user = userService.getCurrentUser();

                GeneratedImage image = repository

                                .findByIdAndUser(

                                                imageId,

                                                user

                                )

                                .orElseThrow(() ->

                                new ImageNotFoundException(

                                                "Image not found"

                                )

                                );

                if (image.getStorageUrl() != null) {

                        try {

                                cloudinaryService.deleteImage(

                                                image.getCloudinaryPublicId()

                                );

                        }

                        catch (Exception ignored) {

                        }

                }

                repository.delete(image);

        }

        @Override
        public Map<String, String> download(

                        String imageId) {

                User user =

                                userService.getCurrentUser();

                GeneratedImage image =

                                repository.findByIdAndUser(

                                                imageId,

                                                user

                                )

                                                .orElseThrow(

                                                                () -> new RuntimeException(

                                                                                "Image not found"

                                                                )

                                                );

                return Map.of(

                                "downloadUrl",

                                image.getStorageUrl()

                );

        }

        @Override
        @Transactional
        public GenerateImageResponse regenerate(

                        String imageId) {

                User user =

                                userService.getCurrentUser();

                GeneratedImage image =

                                repository.findByIdAndUser(

                                                imageId,

                                                user

                                )

                                                .orElseThrow(

                                                                () -> new RuntimeException(

                                                                                "Image not found"

                                                                )

                                                );

                GenerateImageRequest request =

                                new GenerateImageRequest();

                request.setPrompt(

                                image.getPrompt());

                request.setNegativePrompt(

                                image.getNegativePrompt());

                request.setAspectRatio(

                                image.getAspectRatio());

                request.setModel(

                                image.getModel());

                return generateImage(

                                request);

        }

        @Override
        @Transactional
        public void toggleFavorite(String imageId) {

                User user = userService.getCurrentUser();

                GeneratedImage image = repository.findByIdAndUser(
                                imageId,
                                user)
                                .orElseThrow(() -> new ImageNotFoundException(
                                                "Image not found"));

                image.setFavorite(
                                !Boolean.TRUE.equals(
                                                image.getFavorite()));

                repository.save(image);

        }
}