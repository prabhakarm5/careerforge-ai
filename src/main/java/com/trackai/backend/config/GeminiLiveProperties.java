package com.trackai.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gemini.live")
public class GeminiLiveProperties {
    private boolean enabled = true;
    private String model = "gemini-3.1-flash-live-preview";
    private String voice = "Kore";
    private int sessionMinutes = 15;
}
