package com.trackai.backend.entity;

import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "idx_wallet_tx_user_created", columnList = "user_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long amount;

    /*
     * ==========================================================
     * PostgreSQL + MySQL (ACTIVE)
     * ==========================================================
     * EnumType.STRING works with both databases.
     */
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    private FeatureType featureType;

    /*
     * ==========================================================
     * PostgreSQL + MySQL (ACTIVE)
     * ==========================================================
     * VARCHAR is sufficient for transaction descriptions.
     * If in future descriptions become very large,
     * use @Lob + TEXT.
     */
    @Column(length = 500)
    private String description;

    /*
     * ==========================================================
     * PostgreSQL Alternative (Optional)
     * ==========================================================
     * Uncomment if large descriptions are expected.
     */

    // @Lob
    // @Column(columnDefinition = "TEXT")
    // private String description;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /*
     * ==========================================================
     * MySQL (NOTE)
     * ==========================================================
     * No database-specific changes required.
     * This entity is fully compatible with PostgreSQL and MySQL.
     */
}