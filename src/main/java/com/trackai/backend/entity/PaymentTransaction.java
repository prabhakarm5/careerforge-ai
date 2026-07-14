package com.trackai.backend.entity;

import com.trackai.backend.enums.PaymentGateway;
import com.trackai.backend.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_payment_user_status", columnList = "user_id,status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {
    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String subscriptionPlanId;

    @Column(nullable = false, unique = true)
    private String orderId;

    // Final verified amount charged by Razorpay.
    @Column(nullable = false)
    private Long amount;

    private Long originalAmount;
    private Long discountAmount;
    private String promoCode;
    private String promoClaimId;
    private Long bonusTokens;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentGateway gateway;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(unique = true)
    private String paymentId;

    private LocalDateTime updatedAt;
    private String failureReason;

    // Gateway/recovery metadata keeps reconciliation restart-safe and visible.
    private String gatewayStatus;

    @Builder.Default
    @Column
    private Integer reconciliationAttempts = 0;

    private LocalDateTime lastReconciledAt;
    private LocalDateTime settledAt;
}