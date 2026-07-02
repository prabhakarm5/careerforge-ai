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
@ConfigurationProperties(prefix = "openrouter")
public class OpenRouterProperties {

    private String apiKey;

    private String baseUrl;

    private String chatEndpoint;

    private String imageEndpoint;

    private String chatModel;

    private String imageModel;

    private List<ModelInfo> chatModels = new ArrayList<>();

    private List<ModelInfo> imageModels = new ArrayList<>();

    private Integer timeout;

    private String referer;

    private String title;

    public String resolveChatModel(String requestedModel) {
        return resolveModel(requestedModel, chatModel, chatModels);
    }

    public String resolveImageModel(String requestedModel) {
        return resolveModel(requestedModel, imageModel, imageModels);
    }

    public boolean supportsChatModel(String requestedModel) {
        return supportsModel(requestedModel, chatModel, chatModels);
    }

    public boolean supportsImageModel(String requestedModel) {
        return supportsModel(requestedModel, imageModel, imageModels);
    }

    private String resolveModel(String requestedModel, String defaultModel, List<ModelInfo> models) {
        if (requestedModel == null || requestedModel.isBlank() || "openrouter".equalsIgnoreCase(requestedModel)) {
            return defaultModel;
        }

        String normalized = stripPrefix(requestedModel);

        if (models != null) {
            for (ModelInfo model : models) {
                if (Objects.equals(model.getId(), normalized)) {
                    return model.getId();
                }
            }
        }

        return defaultModel;
    }

    private boolean supportsModel(String requestedModel, String defaultModel, List<ModelInfo> models) {
        if (requestedModel == null || requestedModel.isBlank()) {
            return false;
        }

        if ("openrouter".equalsIgnoreCase(requestedModel)) {
            return true;
        }

        String normalized = stripPrefix(requestedModel);

        if (Objects.equals(normalized, defaultModel)) {
            return true;
        }

        if (models == null) {
            return false;
        }

        for (ModelInfo model : models) {
            if (Objects.equals(model.getId(), normalized)) {
                return true;
            }
        }

        return false;
    }

    private String stripPrefix(String modelId) {
        if (modelId != null && modelId.startsWith("openrouter:")) {
            return modelId.substring("openrouter:".length());
        }
        return modelId;
    }

    @Getter
    @Setter
    public static class ModelInfo {
        private String id;
        private String label;
        private String description;
        private boolean vision;
        private String provider = "OPENROUTER";
        private String type = "chat";
    }
}
