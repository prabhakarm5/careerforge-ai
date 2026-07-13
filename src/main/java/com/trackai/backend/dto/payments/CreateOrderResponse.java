package com.trackai.backend.dto.payments;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CreateOrderResponse {
    private String orderId;
    private Long amount;
    private Long originalAmount;
    private Long discountAmount;
    private String appliedPromoCode;
    private String currency;
    private String keyId;
}