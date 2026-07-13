package com.trackai.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jobs.provider")
public class JobSearchProperties {

    private String baseUrl = "https://api.adzuna.com";
    private String appId;
    private String appKey;
    private String defaultCountry = "in";
    private int resultsPerPage = 20;
    private int maxAgeDays = 30;
    private int timeoutSeconds = 20;

    public boolean configured() {
        return appId != null && !appId.isBlank() && appKey != null && !appKey.isBlank();
    }
}