package com.trackai.backend.config;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class OpenRouterConfig {

        private final OpenRouterProperties properties;

        @Bean
        public WebClient openRouterWebClient() {

                return WebClient.builder()

                                .baseUrl(properties.getBaseUrl())

                                .defaultHeader(
                                                HttpHeaders.AUTHORIZATION,
                                                "Bearer " + properties.getApiKey())

                                .defaultHeader(
                                                HttpHeaders.CONTENT_TYPE,
                                                MediaType.APPLICATION_JSON_VALUE)

                                // Helps OpenRouter identify your app
                                .defaultHeader("HTTP-Referer", "http://localhost:5173")
                                .defaultHeader("X-Title", "TrackAI")

                                .build();
        }

}