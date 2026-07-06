package com.trackai.backend.service;

import java.util.List;

import com.trackai.backend.dto.payments.*;

public interface PaymentService {

        // Create order
        CreateOrderResponse createOrder(
                        CreateOrderRequest request);

        // Verify payment
        void verifyPayment(
                        VerifyPaymentRequest request);

        // Current user's payment history
        List<PaymentHistoryResponse> getPaymentHistory();

        // Handle webhook from payment gateway
        void handleWebhook(String payload, String signatureHeader);

        // Mark payment as cancelled (used when user cancels the payment flow)
        void markPaymentCancelled(String orderId, String reason);

}