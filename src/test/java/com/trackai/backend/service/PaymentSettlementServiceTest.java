package com.trackai.backend.service;

import com.trackai.backend.entity.PaymentTransaction;
import com.trackai.backend.entity.SubscriptionPlan;
import com.trackai.backend.enums.PaymentStatus;
import com.trackai.backend.event.PaymentStatusChangedEvent;
import com.trackai.backend.repository.PaymentTransactionRepository;
import com.trackai.backend.repository.SubscriptionPlanRepository;
import com.trackai.backend.service.impl.PaymentSettlementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentSettlementServiceTest {

    private final PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
    private final SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
    private final WalletService wallet = mock(WalletService.class);
    private final PromoCodeService promos = mock(PromoCodeService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private PaymentSettlementServiceImpl service;
    private PaymentTransaction transaction;
    private SubscriptionPlan plan;

    @BeforeEach
    void setUp() {
        service = new PaymentSettlementServiceImpl(payments, plans, wallet, promos, events);
        transaction = PaymentTransaction.builder()
                .id("txn-1")
                .userId("user-1")
                .subscriptionPlanId("plan-1")
                .orderId("order-1")
                .amount(499L)
                .currency("INR")
                .status(PaymentStatus.FAILED)
                .promoClaimId("claim-1")
                .build();
        plan = SubscriptionPlan.builder()
                .id("plan-1")
                .name("Pro")
                .price(499L)
                .tokens(1000L)
                .build();

        when(payments.findByOrderId("order-1")).thenReturn(Optional.of(transaction));
        when(plans.findById("plan-1")).thenReturn(Optional.of(plan));
        when(payments.saveAndFlush(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void capturedGatewayPaymentRecoversLocallyFailedOrderExactlyOnce() {
        boolean recovered = service.settleCaptured("order-1", "pay-1");
        boolean duplicate = service.settleCaptured("order-1", "pay-1");

        assertThat(recovered).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(transaction.getFailureReason()).isNull();
        assertThat(transaction.getGatewayStatus()).isEqualTo("captured");
        assertThat(transaction.getSettledAt()).isNotNull();

        verify(wallet, times(1)).applyPlanToWallet("user-1", plan);
        verify(promos, times(1)).redeemPaymentClaim("claim-1", "order-1", "user-1");
        verify(events, times(1)).publishEvent(any(PaymentStatusChangedEvent.class));
    }

    @Test
    void failedEventNeverDowngradesSuccessfulPayment() {
        transaction.setStatus(PaymentStatus.SUCCESS);

        boolean changed = service.markFailed("order-1", "Late failed webhook", "failed");

        assertThat(changed).isFalse();
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(payments, never()).save(any());
        verifyNoInteractions(wallet, promos, events);
    }
}