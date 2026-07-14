package com.trackai.backend.service;

public interface PaymentSettlementService {
    boolean settleCaptured(String orderId, String paymentId);
    boolean markFailed(String orderId, String reason, String gatewayStatus);
}