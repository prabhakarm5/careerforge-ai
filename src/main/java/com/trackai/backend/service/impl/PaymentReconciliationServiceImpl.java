package com.trackai.backend.service.impl;

import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.trackai.backend.entity.PaymentTransaction;
import com.trackai.backend.enums.PaymentStatus;
import com.trackai.backend.repository.PaymentTransactionRepository;
import com.trackai.backend.service.PaymentReconciliationService;
import com.trackai.backend.service.PaymentSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationServiceImpl implements PaymentReconciliationService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentSettlementService paymentSettlementService;
    private final RazorpayClient razorpayClient;

    @Value("$" + "{app.payment.reconciliation.enabled:true}")
    private boolean enabled;
    @Value("$" + "{app.payment.reconciliation.batch-size:25}")
    private int batchSize;
    @Value("$" + "{app.payment.reconciliation.lookback-days:7}")
    private int lookbackDays;
    @Value("$" + "{app.payment.reconciliation.retry-after-seconds:120}")
    private int retryAfterSeconds;
    @Value("$" + "{app.payment.reconciliation.max-attempts:30}")
    private int maxAttempts;

    @Override
    @Scheduled(
            initialDelayString = "$" + "{app.payment.reconciliation.initial-delay-ms:30000}",
            fixedDelayString = "$" + "{app.payment.reconciliation.fixed-delay-ms:120000}")
    public void reconcileUnsettledPayments() {
        if (!enabled) return;

        LocalDateTime now = LocalDateTime.now();
        List<PaymentTransaction> candidates = paymentTransactionRepository.findReconciliationCandidates(
                Set.of(PaymentStatus.PENDING, PaymentStatus.FAILED),
                now.minusDays(Math.max(1, lookbackDays)),
                now.minusSeconds(Math.max(30, retryAfterSeconds)),
                Math.max(1, maxAttempts),
                PageRequest.of(0, Math.max(1, batchSize)));

        for (PaymentTransaction transaction : candidates) {
            reconcileOne(transaction, now);
        }
    }

    private void reconcileOne(PaymentTransaction transaction, LocalDateTime checkedAt) {
        try {
            List<Payment> gatewayPayments = razorpayClient.orders.fetchPayments(transaction.getOrderId());
            Payment captured = gatewayPayments.stream()
                    .filter(payment -> "captured".equalsIgnoreCase(string(payment, "status")))
                    .findFirst()
                    .orElse(null);

            if (captured != null) {
                paymentSettlementService.settleCaptured(
                        transaction.getOrderId(), string(captured, "id"));
                return;
            }

            String latestStatus = gatewayPayments.isEmpty()
                    ? "created"
                    : string(gatewayPayments.getLast(), "status");
            transaction.setGatewayStatus(latestStatus);
        } catch (Exception exception) {
            log.warn("Payment reconciliation failed for order {}: {}",
                    transaction.getOrderId(), exception.getMessage());
            transaction.setGatewayStatus("reconciliation_error");
        }

        paymentTransactionRepository.recordReconciliationAttempt(
                transaction.getId(), transaction.getGatewayStatus(), checkedAt);
    }

    private String string(Payment payment, String field) {
        Object value = payment.get(field);
        return value == null ? "" : value.toString();
    }
}