package com.trackai.backend.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "openrouter")
public class OpenRouterProperties {

    private String apiKey;

    private String baseUrl;

    private String chatModel;

    private Integer timeout;

    private String imageEndpoint;

    private String imageModel;

    private Long imageTokenl;

}