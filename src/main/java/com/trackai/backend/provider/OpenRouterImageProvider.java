package com.trackai.backend.provider;

import com.trackai.backend.config.OpenRouterProperties;
import com.trackai.backend.dto.image.GenerateImageRequest;
import com.trackai.backend.dto.image.GenerateImageResponse;
import com.trackai.backend.dto.image.provider.OpenRouterImageRequest;
import com.trackai.backend.dto.image.provider.OpenRouterImageResponse;
import com.trackai.backend.exception.OpenRouterException;

import lombok.RequiredArgsConstructor;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class OpenRouterImageProvider implements AIImageProvider {

    private final WebClient openRouterWebClient;

    private final OpenRouterProperties properties;

    @Value("${trackai.tokens.image}")
    private Long imageTokenCost;

    @Override
    public GenerateImageResponse generateImage(GenerateImageRequest request) {

        String model = resolveModel(request);

        OpenRouterImageRequest providerRequest = OpenRouterImageRequest.builder()
                .model(model)
                .prompt(buildPrompt(request))
                .aspect_ratio(resolveAspectRatio(request))
                .n(1)
                .build();

        OpenRouterImageResponse providerResponse = openRouterWebClient
                .post()
                .uri(properties.getImageEndpoint())
                .bodyValue(providerRequest)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(OpenRouterException::new))
                .bodyToMono(OpenRouterImageResponse.class)
                .timeout(Duration.ofSeconds(120))
                .onErrorMap(throwable -> new IllegalStateException(
                        "OpenRouter image request failed", throwable))
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException(
                        "OpenRouter image response is empty."));

        String imageUrl = extractImageUrl(providerResponse);

        return GenerateImageResponse.builder()
                .imageUrl(imageUrl)
                .revisedPrompt(request.getPrompt())
                .model(model)
                .build();
    }

    private String resolveModel(GenerateImageRequest request) {

        if (request.getModel() != null && !request.getModel().isBlank()) {
            return request.getModel();
        }

        return properties.getImageModel();
    }

    private String buildPrompt(GenerateImageRequest request) {

        if (request.getNegativePrompt() == null
                || request.getNegativePrompt().isBlank()) {
            return request.getPrompt();
        }

        return request.getPrompt()
                + "\n\nAvoid: "
                + request.getNegativePrompt();
    }

    private String resolveAspectRatio(GenerateImageRequest request) {

        if (request.getAspectRatio() == null
                || request.getAspectRatio().isBlank()) {
            return "1:1";
        }

        return request.getAspectRatio();
    }

    private String extractImageUrl(OpenRouterImageResponse response) {

        if (response == null
                || response.getData() == null
                || response.getData().isEmpty()
                || response.getData().get(0).getUrl() == null
                || response.getData().get(0).getUrl().isBlank()) {
            throw new IllegalStateException(
                    "OpenRouter did not return an image URL.");
        }

        return response.getData().get(0).getUrl();
    }
}
