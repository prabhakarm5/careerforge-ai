package com.trackai.backend.dto.payments;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderRequest {

    @NotBlank(message = "Plan id is required")
    private String planId;
}