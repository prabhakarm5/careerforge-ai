package com.trackai.backend.event;

import com.trackai.backend.enums.PaymentStatus;

public record PaymentStatusChangedEvent(
        String transactionId,
        PaymentStatus status,
        String reason) {
}