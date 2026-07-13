package com.trackai.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gemini.resume")
public class GeminiResumeProperties {

    private String apiKey;
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private String model;
    private List<ModelInfo> models = new ArrayList<>();
    private int timeoutSeconds = 75;
    private long maxFileBytes = 5 * 1024 * 1024;
    private int maxResumeChars = 50_000;
    private int maxJobDescriptionChars = 20_000;
    private int chatHistoryMessages = 100;
    private int chatHistoryMaxChars = 24_000;
    private int chatMaxOutputTokens = 8_192;
    private int jsonMaxOutputTokens = 8_192;

    /**
     * Resolves only configured models. Arbitrary model IDs from the client are never forwarded
     * to Gemini, which keeps billing and preview-model usage under backend control.
     */
    public String resolveModel(String requestedModel) {
        if (requestedModel == null || requestedModel.isBlank()) {
            return model;
        }

        String normalized = requestedModel.startsWith("models/")
                ? requestedModel.substring("models/".length())
                : requestedModel.trim();

        if (Objects.equals(normalized, model)) {
            return normalized;
        }
        return (models == null ? List.<ModelInfo>of() : models).stream()
                .map(ModelInfo::getId)
                .filter(id -> Objects.equals(id, normalized))
                .findFirst()
                .orElse(null);
    }

    public List<ModelInfo> availableModels() {
        List<ModelInfo> configured = models == null ? new ArrayList<>() : new ArrayList<>(models);
        boolean defaultIsListed = configured.stream()
                .anyMatch(item -> Objects.equals(item.getId(), model));

        // If only the default env value changes, keep it selectable without a Java edit.
        if (!defaultIsListed && model != null && !model.isBlank()) {
            ModelInfo fallback = new ModelInfo();
            fallback.setId(model);
            fallback.setLabel(model);
            fallback.setDescription("Default Gemini resume model");
            fallback.setTier("BALANCED");
            configured.add(0, fallback);
        }
        return List.copyOf(configured);
    }

    @Getter
    @Setter
    public static class ModelInfo {
        private String id;
        private String label;
        private String description;
        private String tier = "BALANCED";
        private boolean preview;
    }
}
