package com.trackai.backend.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackai.backend.config.HuggingFaceImageProperties;
import com.trackai.backend.dto.image.GenerateImageRequest;
import com.trackai.backend.dto.image.GenerateImageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HuggingFaceImageProvider implements AIImageProvider {

    private final HuggingFaceImageProperties properties;

    @Override
    public boolean supports(String modelId) {
        return properties.supports(modelId);
    }

    @Override
    public GenerateImageResponse generateImage(GenerateImageRequest request) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new RuntimeException("Hugging Face image API is not configured. Add HF_TOKEN to the backend environment.");
        }

        HuggingFaceImageProperties.ModelInfo model = properties.resolveModel(request.getModel());
        if (model.isRequiresImageInput() && (request.getImage() == null || request.getImage().isEmpty())) {
            throw new RuntimeException(model.getLabel() + " requires an uploaded reference image.");
        }
        if (request.getImage() != null && !request.getImage().isEmpty() && !model.isSupportsImageInput()) {
            throw new RuntimeException(model.getLabel() + " supports text-to-image only. Choose an edit-capable model for uploads.");
        }
        if (model.getProviderModel() == null || model.getProviderModel().isBlank()) {
            throw new RuntimeException("The selected Hugging Face model is missing its provider route.");
        }

        WebClient providerClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .build();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prompt", request.getPrompt());
        payload.put("num_images", 1);
        payload.put("output_format", "png");
        payload.put("enable_safety_checker", true);
        if (model.getInferenceSteps() > 0) {
            payload.put("num_inference_steps", model.getInferenceSteps());
        }
        addReferenceImage(payload, request, model);

        JsonNode submission = providerClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + model.getProviderModel())
                        .queryParam("_subdomain", "queue")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                        .map(body -> providerError("submission", body)))
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(30));

        String responseUrl = text(submission, "response_url");
        if (responseUrl.isBlank()) {
            throw new RuntimeException("Hugging Face image provider returned an invalid queue response.");
        }

        String requestPath = URI.create(responseUrl).getPath();
        Instant deadline = Instant.now().plusSeconds(Math.max(30, properties.getTimeoutSeconds()));
        boolean completed = false;
        while (Instant.now().isBefore(deadline)) {
            JsonNode status = providerClient.get()
                    .uri(uriBuilder -> uriBuilder.path(requestPath + "/status")
                            .queryParam("_subdomain", "queue").build())
                    .retrieve()
                    .onStatus(code -> code.isError(), response -> response.bodyToMono(String.class)
                            .map(body -> providerError("status", body)))
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(20));

            String state = text(status, "status");
            if ("COMPLETED".equalsIgnoreCase(state)) {
                completed = true;
                break;
            }
            if ("FAILED".equalsIgnoreCase(state) || "CANCELLED".equalsIgnoreCase(state)) {
                throw providerError("generation", status == null ? state : status.toString());
            }
            sleepPollInterval();
        }

        if (!completed) {
            throw new RuntimeException("Image generation timed out. Please retry or choose a faster model.");
        }

        JsonNode result = providerClient.get()
                .uri(uriBuilder -> uriBuilder.path(requestPath)
                        .queryParam("_subdomain", "queue").build())
                .retrieve()
                .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                        .map(body -> providerError("result", body)))
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(30));

        String imageUrl = result == null ? "" : result.path("images").path(0).path("url").asText("");
        if (imageUrl.isBlank()) {
            throw new RuntimeException("Hugging Face image provider completed without an image.");
        }

        byte[] image = downloadImage(imageUrl);
        if (image == null || image.length == 0) {
            throw new RuntimeException("Hugging Face returned an empty image.");
        }

        return GenerateImageResponse.builder()
                .prompt(request.getPrompt())
                .modelId("huggingface:" + model.getId())
                .provider("HUGGING_FACE_FAL")
                .imageBytes(image)
                .build();
    }


    private void addReferenceImage(
            Map<String, Object> payload,
            GenerateImageRequest request,
            HuggingFaceImageProperties.ModelInfo model) {
        if (request.getImage() == null || request.getImage().isEmpty()) return;

        long maxBytes = Math.max(1024 * 1024, properties.getMaxUploadBytes());
        if (request.getImage().getSize() > maxBytes) {
            throw new RuntimeException("Reference image must be smaller than "
                    + readableMegabytes((int) maxBytes) + " MB.");
        }

        String contentType = request.getImage().getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Reference file must be a valid image.");
        }

        String inputField = model.getImageInputField();
        if (inputField == null || inputField.isBlank()) {
            throw new RuntimeException("The selected edit model is missing its image input configuration.");
        }

        try {
            String dataUrl = "data:" + contentType + ";base64,"
                    + Base64.getEncoder().encodeToString(request.getImage().getBytes());
            payload.put(inputField, dataUrl);
        } catch (java.io.IOException error) {
            throw new RuntimeException("Reference image could not be read.", error);
        }
    }
    private byte[] downloadImage(String imageUrl) {
        int maxDownloadBytes = Math.max(1024 * 1024, properties.getMaxDownloadBytes());
        WebClient downloadClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxDownloadBytes))
                .build();

        byte[] image = downloadClient.get()
                .uri(imageUrl)
                .exchangeToMono(response -> readImageResponse(response, maxDownloadBytes))
                .onErrorMap(this::hasDataBufferLimitCause,
                        error -> new RuntimeException(
                                "Generated image is larger than the configured download limit of "
                                        + readableMegabytes(maxDownloadBytes) + " MB.", error))
                .block(Duration.ofSeconds(45));

        if (image != null && image.length > maxDownloadBytes) throw imageTooLarge(maxDownloadBytes);
        return image;
    }

    private Mono<byte[]> readImageResponse(ClientResponse response, int maxDownloadBytes) {
        if (response.statusCode().isError()) {
            return response.bodyToMono(String.class).defaultIfEmpty("")
                    .flatMap(body -> Mono.error(providerError("download", body)));
        }
        long contentLength = response.headers().contentLength().orElse(-1);
        if (contentLength > maxDownloadBytes) return Mono.error(imageTooLarge(maxDownloadBytes));

        MediaType contentType = response.headers().contentType().orElse(null);
        if (contentType != null
                && !"image".equalsIgnoreCase(contentType.getType())
                && !MediaType.APPLICATION_OCTET_STREAM.isCompatibleWith(contentType)) {
            return Mono.error(new RuntimeException(
                    "Hugging Face returned an invalid image response (" + contentType + ")."));
        }
        return response.bodyToMono(byte[].class);
    }

    private RuntimeException imageTooLarge(int maxDownloadBytes) {
        return new RuntimeException("Generated image is larger than the configured download limit of "
                + readableMegabytes(maxDownloadBytes) + " MB.");
    }

    private boolean hasDataBufferLimitCause(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof org.springframework.core.io.buffer.DataBufferLimitException) return true;
            current = current.getCause();
        }
        return false;
    }

    private int readableMegabytes(int bytes) {
        return Math.max(1, bytes / (1024 * 1024));
    }

    private String text(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("");
    }

    private RuntimeException providerError(String stage, String body) {
        String detail = body == null || body.isBlank() ? "No error details were returned." : body;
        return new RuntimeException("Hugging Face image " + stage + " failed: " + detail);
    }

    private void sleepPollInterval() {
        try {
            Thread.sleep(Math.max(250, properties.getPollIntervalMillis()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Image generation was interrupted.", interrupted);
        }
    }
}