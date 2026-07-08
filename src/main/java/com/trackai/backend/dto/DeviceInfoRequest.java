package com.trackai.backend.dto;

import lombok.Data;

@Data
public class DeviceInfoRequest {

    private String browser;

    private String os;

    private String deviceType;

    private String language;

    private String timezone;

    private ScreenInfo screen;

    private HardwareInfo hardware;

    private LocationInfo location;

    private String createdAt;

    @Data
    public static class ScreenInfo {
        private Integer width;
        private Integer height;
        private Integer colorDepth;
        private Double pixelRatio;
    }

    @Data
    public static class HardwareInfo {
        private String platform;
        private Integer cpuCores;
        private Double deviceMemoryGb;
    }

    @Data
    public static class LocationInfo {
        private Double latitude;
        private Double longitude;
        private Integer accuracyMeters;
    }
}