package com.trackai.backend.dto.payments;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderRequest {
    @NotBlank(message = "Plan id is required")
    private String planId;

    @Size(max = 32, message = "Promo code is too long")
    private String promoCode;
}