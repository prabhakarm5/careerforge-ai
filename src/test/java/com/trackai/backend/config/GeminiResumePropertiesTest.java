package com.trackai.backend.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiResumePropertiesTest {

    @Test
    void resolvesOnlyModelsConfiguredByYamlOrEnvironment() {
        GeminiResumeProperties properties = new GeminiResumeProperties();
        properties.setModel("default-model");

        GeminiResumeProperties.ModelInfo fast = new GeminiResumeProperties.ModelInfo();
        fast.setId("fast-model");
        fast.setLabel("Fast");
        properties.setModels(List.of(fast));

        assertThat(properties.resolveModel(null)).isEqualTo("default-model");
        assertThat(properties.resolveModel("fast-model")).isEqualTo("fast-model");
        assertThat(properties.resolveModel("models/fast-model")).isEqualTo("fast-model");
        assertThat(properties.resolveModel("unapproved-model")).isNull();
    }

    @Test
    void keepsEnvironmentOverriddenDefaultVisibleInSelector() {
        GeminiResumeProperties properties = new GeminiResumeProperties();
        properties.setModel("environment-default");
        properties.setModels(List.of());

        assertThat(properties.availableModels())
                .extracting(GeminiResumeProperties.ModelInfo::getId)
                .containsExactly("environment-default");
    }

    @Test
    void webResearchModelFallsBackToConfiguredDefault() {
        GeminiResumeProperties properties = new GeminiResumeProperties();
        properties.setModel("default-model");
        assertThat(properties.getWebResearchModel()).isEqualTo("default-model");

        properties.setWebResearchModel("research-model");
        assertThat(properties.getWebResearchModel()).isEqualTo("research-model");
    }}
