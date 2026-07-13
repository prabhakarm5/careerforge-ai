package com.trackai.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "trackai.monitoring")
public class MonitoringProperties {

    /** Keeps monitoring memory bounded even when traffic spikes. */
    private int maxEvents = 1000;
    private boolean trustProxyHeaders = false;
    private int retentionHours = 24;
}
