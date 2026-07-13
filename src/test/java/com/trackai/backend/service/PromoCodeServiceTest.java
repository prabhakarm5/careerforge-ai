package com.trackai.backend.service;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.admin.PromoCodeResponse;
import com.trackai.backend.entity.PromoClaim;
import com.trackai.backend.entity.PromoCode;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.PaymentStatus;
import com.trackai.backend.enums.PromoAudience;
import com.trackai.backend.enums.PromoRewardType;
import com.trackai.backend.repository.*;
import com.trackai.backend.service.impl.PromoCodeServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromoCodeServiceTest {
    private final PromoCodeRepository promoRepository = mock(PromoCodeRepository.class);
    private final PromoClaimRepository claimRepository = mock(PromoClaimRepository.class);
    private final PaymentTransactionRepository paymentRepository = mock(PaymentTransactionRepository.class);
    private final SubscriptionPlanRepository planRepository = mock(SubscriptionPlanRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final WalletService walletService = mock(WalletService.class);
    private final RedisRateLimitService rateLimitService = mock(RedisRateLimitService.class);
    private final RateLimitProperties limits = new RateLimitProperties();
    private PromoCodeServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new PromoCodeServiceImpl(promoRepository, claimRepository, paymentRepository,
                planRepository, userRepository, walletService, rateLimitService, limits);
        user = User.builder().id("user-1").email("user@example.com").name("User").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(claimRepository.findByUserId("user-1")).thenReturn(List.of());
        when(claimRepository.countAllByPromoCode()).thenReturn(List.of());
        when(rateLimitService.allowRequest(anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(RateLimitResponse.builder().allowed(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.com", "", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void neverRechargedPromoIsVisibleOnlyBeforeFirstSuccessfulPayment() {
        PromoCode promo = promo("FIRST", PromoAudience.NEVER_RECHARGED);
        when(promoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(promo));
        when(paymentRepository.existsByUserIdAndStatus("user-1", PaymentStatus.SUCCESS)).thenReturn(false, true);

        List<PromoCodeResponse> firstVisit = service.getAvailable();
        List<PromoCodeResponse> afterRecharge = service.getAvailable();

        assertThat(firstVisit).singleElement().satisfies(item -> assertThat(item.eligible()).isTrue());
        assertThat(afterRecharge).isEmpty();
    }

    @Test
    void specificUserPromoDoesNotExposeOtherTargetEmails() {
        PromoCode promo = promo("REWARD", PromoAudience.SPECIFIC_USERS);
        promo.setTargetUserEmails(Set.of("user@example.com", "private@example.com"));
        when(promoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(promo));
        when(paymentRepository.existsByUserIdAndStatus("user-1", PaymentStatus.SUCCESS)).thenReturn(false);

        PromoCodeResponse response = service.getAvailable().getFirst();

        assertThat(response.eligible()).isTrue();
        assertThat(response.targetUserEmails()).isEmpty();
    }

    @Test
    void tokenRewardIsGrantedOnlyOnce() {
        PromoCode promo = promo("THANKS", PromoAudience.ALL_USERS);
        promo.setRewardType(PromoRewardType.BONUS_TOKENS);
        promo.setBonusTokens(500L);
        when(promoRepository.findByCodeForUpdate("THANKS")).thenReturn(Optional.of(promo));
        when(claimRepository.countByPromoCodeId(promo.getId())).thenReturn(0L);
        when(claimRepository.findForUpdate(promo.getId(), user.getId())).thenReturn(Optional.empty());
        when(claimRepository.saveAndFlush(any(PromoClaim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.claim("thanks");

        assertThat(result.rewardGranted()).isTrue();
        verify(walletService, times(1)).addTokens(eq("user-1"), eq(500L), any(), contains("THANKS"));
        verify(claimRepository).save(argThat(claim -> claim.getRedeemedAt() != null));
    }

    private PromoCode promo(String code, PromoAudience audience) {
        return PromoCode.builder().id("promo-" + code).code(code).title(code)
                .discountPercent(10).bonusTokens(0L).rewardType(PromoRewardType.DISCOUNT)
                .audience(audience).maxTotalClaims(0).targetUserEmails(Set.of())
                .active(true).build();
    }
}