package com.trackai.backend.entity;

import com.trackai.backend.enums.PromoClaimStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "promo_claims",
        uniqueConstraints = @UniqueConstraint(name = "uk_promo_claim_user", columnNames = {"promo_code_id", "user_id"}),
        indexes = {
                @Index(name = "idx_promo_claim_code_status", columnList = "promo_code_id,status"),
                @Index(name = "idx_promo_claim_user", columnList = "user_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoClaim {
    @Id
    private String id;

    @Column(name = "promo_code_id", nullable = false)
    private String promoCodeId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PromoClaimStatus status;

    @Column(name = "reserved_order_id")
    private String reservedOrderId;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt;

    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;
}