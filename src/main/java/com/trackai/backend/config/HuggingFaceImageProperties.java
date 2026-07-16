package com.trackai.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "huggingface.image")
public class HuggingFaceImageProperties {

    private boolean enabled = true;
    private String apiKey;
    private String baseUrl = "https://router.huggingface.co/fal-ai";
    private String model = "black-forest-labs/FLUX.1-schnell";
    private String providerModel = "fal-ai/flux/schnell";
    private String label = "FLUX.1 Schnell";
    private String description = "Fast text-to-image generation through Hugging Face Inference Providers and Fal AI.";
    private int timeoutSeconds = 120;
    private int pollIntervalMillis = 500;
    private int maxDownloadBytes = 15 * 1024 * 1024;
    private int maxUploadBytes = 8 * 1024 * 1024;
    private List<ModelInfo> models = new ArrayList<>();

    public List<ModelInfo> availableModels() {
        if (models != null && !models.isEmpty()) {
            return models.stream().filter(ModelInfo::isEnabled).toList();
        }
        ModelInfo fallback = new ModelInfo();
        fallback.setId(model);
        fallback.setProviderModel(providerModel);
        fallback.setLabel(label);
        fallback.setDescription(description);
        return List.of(fallback);
    }

    public ModelInfo resolveModel(String requestedId) {
        String normalized = requestedId == null ? "" : requestedId.trim();
        if (normalized.startsWith("huggingface:")) {
            normalized = normalized.substring("huggingface:".length());
        }
        final String target = normalized;
        return availableModels().stream()
                .filter(item -> item.getId() != null && item.getId().equals(target))
                .findFirst()
                .orElseGet(() -> availableModels().stream()
                        .filter(ModelInfo::isDefaultModel)
                        .findFirst()
                        .orElse(availableModels().get(0)));
    }

    public boolean supports(String requestedId) {
        if (!enabled || requestedId == null || !requestedId.startsWith("huggingface:")) return false;
        String normalized = requestedId.substring("huggingface:".length());
        return availableModels().stream().anyMatch(item -> normalized.equals(item.getId()));
    }

    @Getter
    @Setter
    public static class ModelInfo {
        private String id;
        private String providerModel;
        private String label;
        private String description;
        private int inferenceSteps = 4;
        private boolean enabled = true;
        private boolean defaultModel;
        private boolean supportsImageInput;
        private boolean requiresImageInput;
        private String imageInputField = "image_url";
    }
}