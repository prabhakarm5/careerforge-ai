package com.trackai.backend.repository;

import com.trackai.backend.entity.PaymentTransaction;
import com.trackai.backend.enums.PaymentStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository
                extends JpaRepository<PaymentTransaction, String> {

        /*
         * ==========================================================
         * PostgreSQL ✅
         * MySQL ✅
         *
         * Ye repository PostgreSQL aur MySQL dono me compatible hai.
         * 
         * @Lock(LockModeType.PESSIMISTIC_WRITE) PostgreSQL support karta hai.
         *
         * Agar future me native SQL queries likhoge tab hi database-specific
         * changes ki zarurat padegi.
         * ==========================================================
         */

        // Find by Order ID (Thread-safe)
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<PaymentTransaction> findByOrderId(String orderId);

        // Find by Payment ID (Thread-safe)
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<PaymentTransaction> findByPaymentId(String paymentId);

        // User Payment History
        List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(String userId);

        boolean existsByUserIdAndStatus(String userId, PaymentStatus status);
}