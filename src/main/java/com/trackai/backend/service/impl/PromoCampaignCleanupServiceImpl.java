package com.trackai.backend.service.impl;

import com.trackai.backend.entity.PromoCode;
import com.trackai.backend.repository.PromoClaimRepository;
import com.trackai.backend.repository.PromoCodeRepository;
import com.trackai.backend.service.PromoCampaignCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoCampaignCleanupServiceImpl implements PromoCampaignCleanupService {
    private final PromoCodeRepository promoCodeRepository;
    private final PromoClaimRepository promoClaimRepository;

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${app.promo.cleanup-interval-ms:300000}")
    public int deleteUnclaimedInactiveCampaigns() {
        LocalDateTime now = LocalDateTime.now();
        List<PromoCode> candidates = promoCodeRepository.findAll().stream()
                .filter(promo -> Boolean.FALSE.equals(promo.getActive())
                        || (promo.getExpiresAt() != null && !promo.getExpiresAt().isAfter(now)))
                .toList();

        int deleted = 0;
        for (PromoCode promo : candidates) {
            promoClaimRepository.deleteByPromoCodeId(promo.getId());
            promoCodeRepository.delete(promo);
            deleted++;
        }
        if (deleted > 0) log.info("Removed {} expired or disabled promo campaigns", deleted);
        return deleted;
    }
}