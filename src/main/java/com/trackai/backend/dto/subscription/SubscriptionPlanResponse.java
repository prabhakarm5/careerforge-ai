package com.trackai.backend.dto.subscription;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SubscriptionPlanResponse {

    private String id;

    private String name;

    private Long price;

    private Long tokens;

    private String description;

    private Boolean active;
}