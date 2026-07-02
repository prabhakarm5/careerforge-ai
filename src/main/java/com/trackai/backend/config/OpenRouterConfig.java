package com.trackai.backend.config;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class OpenRouterConfig {

        private final OpenRouterProperties properties;

        @Bean
        public WebClient openRouterWebClient() {

                /*
                 * OLD CODE:
                 *
                 * return WebClient.builder()
                 *                 .baseUrl(properties.getBaseUrl())
                 *                 .defaultHeader(HttpHeaders.AUTHORIZATION,
                 *                                 "Bearer " + properties.getApiKey())
                 *                 .defaultHeader(HttpHeaders.CONTENT_TYPE,
                 *                                 MediaType.APPLICATION_JSON_VALUE)
                 *                 .defaultHeader("HTTP-Referer",
                 *                                 properties.getReferer())
                 *                 .defaultHeader("X-Title",
                 *                                 properties.getTitle())
                 *                 .build();
                 */

                ExchangeStrategies strategies = ExchangeStrategies.builder()
                                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                                .build();

                return WebClient.builder()
                                .baseUrl(properties.getBaseUrl())
                                .exchangeStrategies(strategies)
                                .defaultHeader(HttpHeaders.AUTHORIZATION,
                                                "Bearer " + properties.getApiKey())
                                .defaultHeader(HttpHeaders.CONTENT_TYPE,
                                                MediaType.APPLICATION_JSON_VALUE)
                                .defaultHeader("HTTP-Referer",
                                                properties.getReferer())
                                .defaultHeader("X-Title",
                                                properties.getTitle())
                                .build();

        }

}