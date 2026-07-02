package com.trackai.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the "groq.*" section from application.yml — including the
 * multi-model list used to populate the model-selector dropdown on
 * the frontend (/api/chat/models).
 *
 * Add/remove models by editing application.yml only — no code change
 * needed.
 */
@Configuration
@ConfigurationProperties(prefix = "groq")
@Data
public class GroqModelConfig {

    private String apiKey;
    private String url;
    private String defaultModel;
    private Integer maxTokens = 8192;
    private List<ModelInfo> models = new ArrayList<>();

    @Data
    public static class ModelInfo {
        private String id;
        private String label;
        private String description;
        private boolean vision;
        private String provider = "GROQ";
        private String type = "chat";
    }

    /**
     * Resolves a requested model id against the configured whitelist.
     * Falls back to the default model if the requested id is blank,
     * unknown, or not configured — so a stale/tampered "model" field
     * from the client can never bypass the whitelist.
     */
    public ModelInfo resolveModel(String requestedId) {
        if (requestedId != null && !requestedId.isBlank()) {
            for (ModelInfo m : models) {
                if (m.getId().equals(requestedId)) {
                    return m;
                }
            }
        }
        for (ModelInfo m : models) {
            if (m.getId().equals(defaultModel)) {
                return m;
            }
        }
        // last-resort fallback so the app never hard-crashes on bad config
        ModelInfo fallback = new ModelInfo();
        fallback.setId(defaultModel);
        fallback.setLabel(defaultModel);
        fallback.setVision(false);
        return fallback;
    }

    /**
     * Returns the next best model to retry with when the current one is
     * rate-limited (429) by Groq. Picks the first configured model that
     * is NOT the one that just failed, so streaming can transparently
     * "hop" to another model without user intervention. If only one
     * model is configured, returns that same model (no alternative
     * available — caller should treat this as a hard failure).
     */
    public ModelInfo getFallbackModel(String currentModelId) {
        if (models == null || models.isEmpty()) {
            return resolveModel(null);
        }
        for (ModelInfo m : models) {
            if (!m.getId().equals(currentModelId)) {
                return m;
            }
        }
        // only one model configured — nothing to fall back to
        return models.get(0);
    }
}
