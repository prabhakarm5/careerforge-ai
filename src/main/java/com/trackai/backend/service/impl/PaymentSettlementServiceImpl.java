package com.trackai.backend.service.impl;

import com.trackai.backend.entity.PaymentTransaction;
import com.trackai.backend.entity.SubscriptionPlan;
import com.trackai.backend.enums.PaymentStatus;
import com.trackai.backend.event.PaymentStatusChangedEvent;
import com.trackai.backend.repository.PaymentTransactionRepository;
import com.trackai.backend.repository.SubscriptionPlanRepository;
import com.trackai.backend.service.PaymentSettlementService;
import com.trackai.backend.service.PromoCodeService;
import com.trackai.backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSettlementServiceImpl implements PaymentSettlementService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final WalletService walletService;
    private final PromoCodeService promoCodeService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public boolean settleCaptured(String orderId, String paymentId) {
        if (orderId == null || orderId.isBlank() || paymentId == null || paymentId.isBlank()) {
            throw new RuntimeException("Gateway payment identifiers are missing");
        }

        PaymentTransaction transaction = paymentTransactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order " + orderId));

        // Browser verify, webhook and recovery can race. The repository lock
        // ensures the wallet receives this payment exactly once.
        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Payment {} is already settled; duplicate event ignored", orderId);
            return false;
        }
        if (transaction.getPaymentId() != null && !transaction.getPaymentId().equals(paymentId)) {
            throw new RuntimeException("Order is already linked to a different gateway payment");
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(transaction.getSubscriptionPlanId())
                .orElseThrow(() -> new RuntimeException("Plan not found"));

        try {
            walletService.applyPlanToWallet(transaction.getUserId(), plan);
            promoCodeService.redeemPaymentClaim(
                    transaction.getPromoClaimId(), orderId, transaction.getUserId());

            transaction.setPaymentId(paymentId);
            transaction.setStatus(PaymentStatus.SUCCESS);
            transaction.setGatewayStatus("captured");
            transaction.setFailureReason(null);
            transaction.setSettledAt(LocalDateTime.now());
            transaction.setUpdatedAt(LocalDateTime.now());
            paymentTransactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException exception) {
            throw new RuntimeException("Gateway payment was already used by another order", exception);
        }

        eventPublisher.publishEvent(new PaymentStatusChangedEvent(
                transaction.getId(), PaymentStatus.SUCCESS, null));
        return true;
    }

    @Override
    @Transactional
    public boolean markFailed(String orderId, String reason, String gatewayStatus) {
        PaymentTransaction transaction = paymentTransactionRepository.findByOrderId(orderId).orElse(null);
        if (transaction == null || transaction.getStatus() == PaymentStatus.SUCCESS) {
            return false;
        }

        String safeReason = reason == null || reason.isBlank() ? "Payment failed" : reason;
        boolean changed = transaction.getStatus() != PaymentStatus.FAILED
                || !safeReason.equals(transaction.getFailureReason());

        transaction.setStatus(PaymentStatus.FAILED);
        transaction.setFailureReason(safeReason);
        transaction.setGatewayStatus(gatewayStatus);
        transaction.setUpdatedAt(LocalDateTime.now());
        paymentTransactionRepository.save(transaction);

        if (changed) {
            eventPublisher.publishEvent(new PaymentStatusChangedEvent(
                    transaction.getId(), PaymentStatus.FAILED, safeReason));
        }
        return changed;
    }
}