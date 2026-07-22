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

    @Test
    void upgradesPublicOAuthCallbacksToHttpsButKeepsLocalhostHttp() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(CareerForgeSecretsEnvironmentPostProcessor.SECRET_ENV_NAME,
                        "{\"GITHUB_OAUTH2_REDIRECT_URI\":\"http://dr07bawk90aps.cloudfront.net/login/oauth2/code/github\"," +
                                "\"GOOGLE_OAUTH2_REDIRECT_URI\":\"http://localhost:9092/login/oauth2/code/google\"}");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("GITHUB_OAUTH2_REDIRECT_URI"))
                .isEqualTo("https://dr07bawk90aps.cloudfront.net/login/oauth2/code/github");
        assertThat(environment.getProperty("GOOGLE_OAUTH2_REDIRECT_URI"))
                .isEqualTo("http://localhost:9092/login/oauth2/code/google");
    }
}
