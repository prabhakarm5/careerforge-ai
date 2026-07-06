package com.trackai.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private Long totalTokens;

    @Column(nullable = false)
    private Long usedTokens;

    @Column(nullable = false)
    private Long remainingTokens;

    /*
     * ==========================================================
     * NEW: tracks which plan the user currently has active.
     * Nullable on purpose — a brand-new wallet (welcome bonus only,
     * no plan purchased yet) has no plan, and frontend should show
     * "Free" in that case rather than crashing on a null field.
     * ==========================================================
     */
    @Column
    private String currentPlanId;

    @Column
    private String currentPlanName;

    /*
     * ==========================================================
     * PostgreSQL + MySQL (ACTIVE)
     * ==========================================================
     * LocalDateTime is supported by both databases.
     * Hibernate maps it automatically.
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /*
     * ==========================================================
     * Automatically update updatedAt before UPDATE
     * ==========================================================
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /*
     * ==========================================================
     * MySQL (NOTE)
     * ==========================================================
     * No database-specific changes required.
     * This entity is fully compatible with PostgreSQL and MySQL.
     */
}