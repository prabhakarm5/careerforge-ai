package com.trackai.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(CorsConfig.AppCorsProperties.class)
public class CorsConfig {

        private final AppCorsProperties corsProperties;

        public CorsConfig(AppCorsProperties corsProperties) {
                this.corsProperties = corsProperties;
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {

                CorsConfiguration configuration = new CorsConfiguration();

                List<String> allowedOrigins = cleanAndUnique(
                                corsProperties.getAllowedOrigins());

                if (allowedOrigins.isEmpty()) {
                        throw new IllegalStateException(
                                        "CORS allowed origins missing. Set app.frontend-url or CORS_ALLOWED_ORIGINS.");
                }

                configuration.setAllowedOrigins(allowedOrigins);

                configuration.setAllowedMethods(List.of(
                                "GET",
                                "POST",
                                "PUT",
                                "PATCH",
                                "DELETE",
                                "OPTIONS"));

                configuration.setAllowedHeaders(List.of(
                                "Authorization",
                                "Content-Type",
                                "Accept",
                                "Origin",
                                "X-Requested-With",
                                "X-Client-Timezone",
                                "X-Client-Locale"));

                configuration.setExposedHeaders(List.of(
                                "Authorization"));

                // Important for httpOnly cookies
                configuration.setAllowCredentials(true);

                // Browser preflight cache: 1 hour
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

                source.registerCorsConfiguration("/**", configuration);

                return source;
        }

        private List<String> cleanAndUnique(List<String> values) {

                if (values == null || values.isEmpty()) {
                        return List.of();
                }

                Set<String> cleaned = new LinkedHashSet<>();

                for (String value : values) {
                        if (value != null && !value.trim().isBlank()) {
                                cleaned.add(value.trim().replaceAll("/+$", ""));
                        }
                }

                return new ArrayList<>(cleaned);
        }

        @ConfigurationProperties(prefix = "app.cors")
        public static class AppCorsProperties {

                private List<String> allowedOrigins = new ArrayList<>();

                public List<String> getAllowedOrigins() {
                        return allowedOrigins;
                }

                public void setAllowedOrigins(List<String> allowedOrigins) {
                        this.allowedOrigins = allowedOrigins;
                }
        }
}