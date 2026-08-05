package com.trackai.backend.service.impl;

import com.trackai.backend.config.HuggingFaceImageProperties;
import com.trackai.backend.exception.ImageDownloadException;
import com.trackai.backend.service.ImageDownloaderService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class ImageDownloaderServiceImpl
        implements ImageDownloaderService {

    private final WebClient.Builder webClientBuilder;
    private final HuggingFaceImageProperties imageProperties;

    @Override
    public byte[] downloadImage(
            String imageUrl) {

        try {

            return webClientBuilder

                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(
                            Math.max(1024 * 1024, imageProperties.getMaxDownloadBytes())))

                    .build()
                    .get()

                    .uri(imageUrl)

                    .retrieve()

                    .bodyToMono(byte[].class)

                    .block();

        }

        catch (Exception e) {

            throw new ImageDownloadException(

                    "Unable to download generated image : "

                            + e.getMessage()

            );

        }

    }

}