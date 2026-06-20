package com.trackai.backend.dto;

import com.trackai.backend.enums.FeatureType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsumeTokenRequest {

    private Long amount;

    private FeatureType featureType;

    private String description;
}