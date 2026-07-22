package com.trackai.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class CareerForgeSecretsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SECRET_ENV_NAME = "CAREERFORGE_PROD_ENV_JSON";
    private static final String PROPERTY_SOURCE_NAME = "careerForgeAwsSecrets";
    private static final Pattern VALID_KEY = Pattern.compile("[A-Z][A-Z0-9_]*");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String payload = environment.getProperty(SECRET_ENV_NAME);
        if (payload == null || payload.isBlank()) {
            return;
        }

        Map<String, Object> secrets = parseSecrets(payload);
        MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, secrets);
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addAfter(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    propertySource);
        } else {
            environment.getPropertySources().addFirst(propertySource);
        }
    }

    Map<String, Object> parseSecrets(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Production secret must be a JSON object");
            }

            Map<String, Object> secrets = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!VALID_KEY.matcher(field.getKey()).matches()) {
                    throw new IllegalArgumentException("Invalid production environment key: " + field.getKey());
                }
                if (!field.getValue().isValueNode()) {
                    throw new IllegalArgumentException("Nested values are not supported for key: " + field.getKey());
                }
                secrets.put(field.getKey(), field.getValue().isNull() ? "" : field.getValue().asText());
            }

            if (secrets.isEmpty()) {
                throw new IllegalArgumentException("Production secret does not contain environment values");
            }
            return secrets;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to load CareerForge production environment. Secret values were not logged.",
                    exception);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}