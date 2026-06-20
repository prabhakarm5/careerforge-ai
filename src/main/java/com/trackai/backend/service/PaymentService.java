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
}