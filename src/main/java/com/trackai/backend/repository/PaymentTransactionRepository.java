package com.trackai.backend.repository;

import com.trackai.backend.entity.PaymentTransaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository
                extends JpaRepository<PaymentTransaction, String> {

        // Find by order id
        Optional<PaymentTransaction> findByOrderId(
                        String orderId);

        // Find by payment id
        Optional<PaymentTransaction> findByPaymentId(
                        String paymentId);

        // User payment history
        List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(
                        String userId);
}