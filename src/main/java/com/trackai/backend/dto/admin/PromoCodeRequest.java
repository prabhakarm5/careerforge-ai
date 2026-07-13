package com.trackai.backend.dto.admin;

import com.trackai.backend.enums.PromoAudience;
import com.trackai.backend.enums.PromoRewardType;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.Set;

public record PromoCodeRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 80) String title,
        @Size(max = 300) String description,
        @NotNull @Min(0) @Max(90) Integer discountPercent,
        @NotNull @Min(0) Long bonusTokens,
        @NotNull PromoRewardType rewardType,
        @NotNull PromoAudience audience,
        String rewardPlanId,
        @NotNull @Min(0) Integer maxTotalClaims,
        Set<String> targetUserEmails,
        @NotNull Boolean active,
        LocalDateTime validFrom,
        LocalDateTime expiresAt) {
}