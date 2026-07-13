package com.trackai.backend.dto.promo;

import com.trackai.backend.dto.admin.PromoCodeResponse;

public record PromoClaimResponse(
        String message,
        boolean rewardGranted,
        PromoCodeResponse promo) {
}