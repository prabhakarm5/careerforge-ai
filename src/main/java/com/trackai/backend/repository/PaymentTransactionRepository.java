package com.trackai.backend.repository;

import com.trackai.backend.entity.PaymentTransaction;
import com.trackai.backend.enums.PaymentStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
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

        Optional<PaymentTransaction> findByOrderIdAndUserId(String orderId, String userId);

        // Find by Payment ID (Thread-safe)
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<PaymentTransaction> findByPaymentId(String paymentId);

        // User Payment History
        List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(String userId);

        boolean existsByUserIdAndStatus(String userId, PaymentStatus status);

        @Query("""
                select p from PaymentTransaction p
                where p.status in :statuses
                  and p.createdAt >= :cutoff
                  and coalesce(p.reconciliationAttempts, 0) < :maxAttempts
                  and (p.lastReconciledAt is null or p.lastReconciledAt <= :retryBefore)
                order by p.createdAt asc
                """)
        List<PaymentTransaction> findReconciliationCandidates(
                        @Param("statuses") Collection<PaymentStatus> statuses,
                        @Param("cutoff") LocalDateTime cutoff,
                        @Param("retryBefore") LocalDateTime retryBefore,
                        @Param("maxAttempts") int maxAttempts,
                        Pageable pageable);

        @Modifying
        @Transactional
        @Query("""
                update PaymentTransaction p
                set p.gatewayStatus = :gatewayStatus,
                    p.lastReconciledAt = :checkedAt,
                    p.reconciliationAttempts = coalesce(p.reconciliationAttempts, 0) + 1
                where p.id = :id and p.status <> com.trackai.backend.enums.PaymentStatus.SUCCESS
                """)
        int recordReconciliationAttempt(
                        @Param("id") String id,
                        @Param("gatewayStatus") String gatewayStatus,
                        @Param("checkedAt") LocalDateTime checkedAt);
}