package com.trackai.backend.dto.subscription;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSubscriptionPlanRequest {

    @NotBlank
    private String name;

    @NotNull
    @Min(0)
    private Long price;

    @NotNull
    @Min(0)
    private Long tokens;

    private String description;
}