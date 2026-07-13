package com.trackai.backend.entity;

import com.trackai.backend.enums.PromoAudience;
import com.trackai.backend.enums.PromoRewardType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "promo_codes", indexes = {
        @Index(name = "idx_promo_active_dates", columnList = "active, valid_from, expires_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoCode {
    @Id
    private String id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(length = 300)
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private Integer discountPercent = 0;

    @Builder.Default
    @Column(nullable = false)
    private Long bonusTokens = 0L;

    // The reward type decides whether checkout is discounted or a reward is granted immediately.
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private PromoRewardType rewardType = PromoRewardType.DISCOUNT;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private PromoAudience audience = PromoAudience.ALL_USERS;

    @Column(name = "reward_plan_id")
    private String rewardPlanId;

    // Zero means unlimited campaign-wide claims. Every user can still claim only once.
    @Builder.Default
    @Column(name = "max_total_claims")
    private Integer maxTotalClaims = 0;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "promo_target_users", joinColumns = @JoinColumn(name = "promo_id"))
    @Column(name = "email", nullable = false, length = 180)
    private Set<String> targetUserEmails = new HashSet<>();

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}