package com.trackai.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.image.reference")
public class ImageReferenceProperties {

    private long maxUploadBytes = 8L * 1024 * 1024;
    private int maxDimension = 2048;
    private long maxPixels = 40_000_000L;
    private float pdfDpi = 144f;
}
