package com.trackai.backend.repository;

import com.trackai.backend.entity.PaymentTransaction;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository
                extends JpaRepository<PaymentTransaction, String> {

        // Find by order id
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<PaymentTransaction> findByOrderId(
                        String orderId);

        // Find by payment id
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<PaymentTransaction> findByPaymentId(
                        String paymentId);

        // User payment history
        List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(
                        String userId);
}