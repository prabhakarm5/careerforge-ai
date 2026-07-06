package com.trackai.backend.entity;

import com.trackai.backend.enums.PaymentGateway;
import com.trackai.backend.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
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

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private String currency;

    /*
     * ==========================================================
     * PostgreSQL + MySQL (ACTIVE)
     * ==========================================================
     * EnumType.STRING is fully compatible with both databases.
     */
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentGateway gateway;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // add these fields to your existing PaymentTransaction entity
    @Column(unique = true)
    private String paymentId; // unique constraint -> prevents same payment being processed twice

    private LocalDateTime updatedAt;

    private String failureReason; // for failed payments

    /*
     * ==========================================================
     * MySQL (NOTE)
     * ==========================================================
     * No database-specific changes required.
     * This entity works with both PostgreSQL and MySQL.
     */
}