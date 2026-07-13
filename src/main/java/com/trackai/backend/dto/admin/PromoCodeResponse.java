package com.trackai.backend.dto.admin;

import com.trackai.backend.entity.PromoCode;
import com.trackai.backend.enums.PromoAudience;
import com.trackai.backend.enums.PromoRewardType;

import java.time.LocalDateTime;
import java.util.Set;

public record PromoCodeResponse(
        String id,
        String code,
        String title,
        String description,
        int discountPercent,
        long bonusTokens,
        PromoRewardType rewardType,
        PromoAudience audience,
        String rewardPlanId,
        int maxTotalClaims,
        long totalClaims,
        Set<String> targetUserEmails,
        boolean active,
        boolean currentlyAvailable,
        boolean eligible,
        String eligibilityMessage,
        String claimStatus,
        LocalDateTime validFrom,
        LocalDateTime expiresAt,
        LocalDateTime createdAt) {

    public static PromoCodeResponse admin(PromoCode promo, long totalClaims) {
        return from(promo, totalClaims, true, null, null, true);
    }

    public static PromoCodeResponse forUser(
            PromoCode promo, long totalClaims, boolean eligible, String message, String claimStatus) {
        return from(promo, totalClaims, eligible, message, claimStatus, false);
    }

    private static PromoCodeResponse from(
            PromoCode promo, long totalClaims, boolean eligible, String message, String claimStatus, boolean exposeTargets) {
        LocalDateTime now = LocalDateTime.now();
        boolean available = Boolean.TRUE.equals(promo.getActive())
                && (promo.getValidFrom() == null || !promo.getValidFrom().isAfter(now))
                && (promo.getExpiresAt() == null || promo.getExpiresAt().isAfter(now))
                && (promo.getMaxTotalClaims() == null || promo.getMaxTotalClaims() == 0
                    || totalClaims < promo.getMaxTotalClaims());
        return new PromoCodeResponse(
                promo.getId(), promo.getCode(), promo.getTitle(), promo.getDescription(),
                value(promo.getDiscountPercent()), value(promo.getBonusTokens()), promo.getRewardType(),
                promo.getAudience(), promo.getRewardPlanId(), value(promo.getMaxTotalClaims()), totalClaims,
                !exposeTargets || promo.getTargetUserEmails() == null ? Set.of() : Set.copyOf(promo.getTargetUserEmails()),
                Boolean.TRUE.equals(promo.getActive()), available, eligible, message, claimStatus,
                promo.getValidFrom(), promo.getExpiresAt(), promo.getCreatedAt());
    }

    private static int value(Integer value) { return value == null ? 0 : value; }
    private static long value(Long value) { return value == null ? 0L : value; }
}