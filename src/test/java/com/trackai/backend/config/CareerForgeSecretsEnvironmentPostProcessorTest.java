package com.trackai.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CareerForgeSecretsEnvironmentPostProcessorTest {

    private final CareerForgeSecretsEnvironmentPostProcessor processor =
            new CareerForgeSecretsEnvironmentPostProcessor();

    @Test
    void loadsJsonValuesAsSpringProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(CareerForgeSecretsEnvironmentPostProcessor.SECRET_ENV_NAME,
                        "{\"DB_URL\":\"jdbc:postgresql://db/prod\",\"JWT_SECRET\":\"secure-value\"}");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("DB_URL")).isEqualTo("jdbc:postgresql://db/prod");
        assertThat(environment.getProperty("JWT_SECRET")).isEqualTo("secure-value");
    }

    @Test
    void rejectsNestedSecretValuesWithoutLoggingThem() {
        assertThatThrownBy(() -> processor.parseSecrets("{\"DB\":{\"PASSWORD\":\"hidden\"}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Secret values were not logged")
                .hasMessageNotContaining("hidden");
    }
}