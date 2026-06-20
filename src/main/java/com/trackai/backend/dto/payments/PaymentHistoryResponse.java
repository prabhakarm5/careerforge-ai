package com.trackai.backend.dto.payments;

import com.trackai.backend.enums.PaymentGateway;
import com.trackai.backend.enums.PaymentStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PaymentHistoryResponse {

    private String orderId;

    private String paymentId;

    private Long amount;

    private String currency;

    private PaymentStatus status;

    private PaymentGateway gateway;

    private LocalDateTime createdAt;
}