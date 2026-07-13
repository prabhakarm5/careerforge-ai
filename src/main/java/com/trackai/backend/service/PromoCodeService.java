package com.trackai.backend.service;

import com.trackai.backend.dto.admin.PromoCodeRequest;
import com.trackai.backend.dto.admin.PromoCodeResponse;
import com.trackai.backend.dto.promo.PromoClaimResponse;

import java.util.List;

public interface PromoCodeService {
    List<PromoCodeResponse> getAll();
    List<PromoCodeResponse> getAvailable();
    PromoClaimResponse claim(String rawCode);
    PromoApplication reserveForOrder(String userId, String rawCode, String orderReference);
    void attachOrder(String claimId, String temporaryReference, String orderId);
    void releaseReservation(String claimId, String orderId);
    void redeemPaymentClaim(String claimId, String orderId, String userId);
    PromoCodeResponse create(PromoCodeRequest request);
    PromoCodeResponse update(String id, PromoCodeRequest request);
    void delete(String id);

    record PromoApplication(String claimId, String code, int discountPercent, long bonusTokens) {
        public static PromoApplication none() { return new PromoApplication(null, null, 0, 0); }
    }
}