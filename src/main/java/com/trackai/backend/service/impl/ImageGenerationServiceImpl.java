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

        @Override
        @Transactional
        public GenerateImageResponse generateImage(
                        GenerateImageRequest request) {

                // Wallet Check
                walletService.checkImageGenerationTokens();

                // Generate from OpenRouter
                GenerateImageResponse response = imageProvider.generateImage(request);

                // URL or Base64
                byte[] imageBytes;

                if (response.getImageBytes() != null) {

                        imageBytes = response.getImageBytes();

                } else {

                        imageBytes = imageDownloaderService.downloadImage(

                                        response.getImageUrl()

                        );

                }

                // Upload to Cloudinary
                CloudinaryUploadResponse upload =

                                cloudinaryService.uploadGeneratedImage(

                                                imageBytes

                                );

                // Current User
                User user = userService.getCurrentUser();

                // Save
                GeneratedImage image = GeneratedImage.builder()

                                .prompt(request.getPrompt())

                                .imageUrl(

                                                response.getImageUrl() == null
                                                                ? upload.getSecureUrl()
                                                                : response.getImageUrl()

                                )

                                .storageUrl(

                                                upload.getSecureUrl()

                                )

                                .provider(

                                                response.getProvider()

                                )

                                .providerImageId(

                                                response.getProviderImageId()

                                )

                                .cloudinaryPublicId(

                                                upload.getPublicId()

                                )

                                .tokensUsed(

                                                tokenProperties.getImage()

                                )

                                .status(

                                                ImageStatus.COMPLETED.name()

                                )

                                .favorite(false)

                                .user(user)

                                .build();

                repository.save(image);

                // Consume Wallet
                walletService.consumeImageTokens(

                                tokenProperties.getImage()

                );

                // Return Response
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

                                                .imageUrl(image.getImageUrl())

                                                .storageUrl(image.getStorageUrl())

                                                .tokensUsed(image.getTokensUsed())

                                                .favorite(image.getFavorite())

                                                .createdAt(image.getCreatedAt())

                                                .build()

                                )

                                .toList();

        }

        @Override
        @Transactional
        public void delete(
                        String imageId) {

                User user = userService.getCurrentUser();

                GeneratedImage image = repository

                                .findByIdAndUser(
                                                imageId,
                                                user)

                                .orElseThrow(() ->

                                new ImageNotFoundException(
                                                "Image not found"));

                if (image.getCloudinaryPublicId() != null
                                && !image.getCloudinaryPublicId().isBlank()) {

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
        @Transactional
        public void toggleFavorite(
                        String imageId) {

                User user = userService.getCurrentUser();

                GeneratedImage image = repository

                                .findByIdAndUser(
                                                imageId,
                                                user)

                                .orElseThrow(() ->

                                new ImageNotFoundException(
                                                "Image not found"));

                image.setFavorite(

                                !Boolean.TRUE.equals(
                                                image.getFavorite())

                );

                repository.save(image);

        }

        @Override
        @Transactional
        public GenerateImageResponse regenerate(
                        String imageId) {

                User user = userService.getCurrentUser();

                GeneratedImage image = repository

                                .findByIdAndUser(
                                                imageId,
                                                user)

                                .orElseThrow(() ->

                                new ImageNotFoundException(
                                                "Image not found"));

                GenerateImageRequest request = new GenerateImageRequest();

                request.setPrompt(

                                image.getPrompt()

                );

                return generateImage(

                                request

                );

        }

        @Override
        public Map<String, String> download(String imageId) {

                User user = userService.getCurrentUser();

                GeneratedImage image = repository

                                .findByIdAndUser(imageId, user)

                                .orElseThrow(() -> new ImageNotFoundException("Image not found"));

                return Map.of(
                                "downloadUrl",
                                image.getStorageUrl());
        }
}