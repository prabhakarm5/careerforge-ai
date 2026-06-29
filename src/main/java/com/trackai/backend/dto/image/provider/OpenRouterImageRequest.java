package com.trackai.backend.dto.image.provider;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OpenRouterImageRequest {

    private String model;

    private String prompt;

    private String aspect_ratio;

    private Integer n;

}