package com.trackai.backend.service.impl;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.payments.CreateOrderRequest;
import com.trackai.backend.dto.payments.CreateOrderResponse;
import com.trackai.backend.dto.payments.PaymentHistoryResponse;
import com.trackai.backend.dto.payments.VerifyPaymentRequest;
import com.trackai.backend.entity.PaymentTransaction;
import com.trackai.backend.entity.SubscriptionPlan;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.PaymentGateway;
import com.trackai.backend.enums.PaymentStatus;
import com.trackai.backend.repository.PaymentTransactionRepository;
import com.trackai.backend.repository.SubscriptionPlanRepository;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.security.JwtUserPrincipal;
import com.trackai.backend.service.PaymentService;
import com.trackai.backend.service.PaymentSettlementService;
import com.trackai.backend.service.PromoCodeService;
import com.trackai.backend.service.PromoCodeService.PromoApplication;
import com.trackai.backend.service.RedisRateLimitService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

        private final UserRepository userRepository;
        private final SubscriptionPlanRepository subscriptionPlanRepository;
        private final PaymentTransactionRepository paymentTransactionRepository;
        private final RazorpayClient razorpayClient;
        private final RedisRateLimitService redisRateLimitService;
        private final RateLimitProperties rateLimitProperties;
        private final PromoCodeService promoCodeService;
        private final PaymentSettlementService paymentSettlementService;

        @Value("${razorpay.key-id}")
        private String keyId;

        @Value("${razorpay.key-secret}")
        private String keySecret;

        @Value("${razorpay.webhook-secret}")
        private String webhookSecret;

        private User getAuthenticatedUser() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication.getPrincipal() instanceof JwtUserPrincipal principal
                                && principal.userId() != null && !principal.userId().isBlank()) {
                        return User.builder()
                                        .id(principal.userId())
                                        .email(principal.email())
                                        .role(com.trackai.backend.enums.Role.valueOf(principal.role()))
                                        .build();
                }
                return userRepository.findByEmail(authentication.getName())
                                .orElseThrow(() -> new RuntimeException("User not found"));
        }

        // ───────────────────────── CREATE ORDER ─────────────────────────
        @Override
        
        public CreateOrderResponse createOrder(CreateOrderRequest request) {
                User user = getAuthenticatedUser();
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(
                                "create-order:" + user.getId(),
                                rateLimitProperties.getCreateOrder().getCapacity(),
                                rateLimitProperties.getCreateOrder().getRefillTokens(),
                                rateLimitProperties.getCreateOrder().getRefillMinutes());
                if (!rateLimitResponse.isAllowed()) throw new RuntimeException(rateLimitResponse.getMessage());

                SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getPlanId())
                                .orElseThrow(() -> new RuntimeException("Plan not found"));
                if (Boolean.FALSE.equals(plan.getActive())) throw new RuntimeException("This plan is no longer available");

                String temporaryReference = "pending:" + UUID.randomUUID();
                PromoApplication promo = promoCodeService.reserveForOrder(
                                user.getId(), request.getPromoCode(), temporaryReference);
                long originalAmount = plan.getPrice();
                long discountAmount = Math.min(originalAmount,
                                Math.round(originalAmount * (promo.discountPercent() / 100.0)));
                long payableAmount = originalAmount - discountAmount;
                if (payableAmount < 1) {
                        promoCodeService.releaseReservation(promo.claimId(), temporaryReference);
                        throw new RuntimeException("This checkout amount is invalid. Use a free-plan reward instead.");
                }

                try {
                        JSONObject options = new JSONObject();
                        options.put("amount", payableAmount * 100);
                        options.put("currency", "INR");
                        options.put("receipt", UUID.randomUUID().toString());
                        Order order = razorpayClient.orders.create(options);
                        String orderId = order.get("id").toString();
                        promoCodeService.attachOrder(promo.claimId(), temporaryReference, orderId);

                        PaymentTransaction payment = PaymentTransaction.builder()
                                        .id(UUID.randomUUID().toString()).userId(user.getId())
                                        .subscriptionPlanId(plan.getId()).orderId(orderId)
                                        .amount(payableAmount).originalAmount(originalAmount)
                                        .discountAmount(discountAmount).promoCode(promo.code())
                                        .promoClaimId(promo.claimId()).bonusTokens(promo.bonusTokens())
                                        .currency("INR").status(PaymentStatus.PENDING)
                                        .gateway(PaymentGateway.RAZORPAY).createdAt(LocalDateTime.now()).build();
                        paymentTransactionRepository.save(payment);

                        return CreateOrderResponse.builder().orderId(orderId).amount(payableAmount)
                                        .originalAmount(originalAmount).discountAmount(discountAmount)
                                        .appliedPromoCode(promo.code()).currency("INR").keyId(keyId).build();
                } catch (RuntimeException exception) {
                        promoCodeService.releaseReservation(promo.claimId(), null);
                        throw exception;
                } catch (Exception exception) {
                        promoCodeService.releaseReservation(promo.claimId(), null);
                        throw new RuntimeException("Failed to create order: " + exception.getMessage());
                }
        }
        // ───────────────────────── VERIFY PAYMENT (client redirect flow)
        // ─────────────────────────
        @Override
        public void verifyPayment(VerifyPaymentRequest request) {

                User user = getAuthenticatedUser();

                // FIXED: same per-user rate limit key issue as createOrder().
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(
                                "verify-payment:" + user.getId(),
                                rateLimitProperties.getVerifyPayment().getCapacity(),
                                rateLimitProperties.getVerifyPayment().getRefillTokens(),
                                rateLimitProperties.getVerifyPayment().getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {
                        throw new RuntimeException(rateLimitResponse.getMessage());
                }

                try {
                        JSONObject attributes = new JSONObject();
                        attributes.put("razorpay_order_id", request.getRazorpayOrderId());
                        attributes.put("razorpay_payment_id", request.getRazorpayPaymentId());
                        attributes.put("razorpay_signature", request.getRazorpaySignature());

                        boolean verified = Utils.verifyPaymentSignature(attributes, keySecret);

                        if (!verified) {
                                paymentSettlementService.markFailed(
                                                request.getRazorpayOrderId(), "Signature mismatch", "signature_mismatch");
                                throw new RuntimeException("Invalid payment signature");
                        }

                        // ─────────────────────────────────────────────────────────
                        // OWNERSHIP CHECK (fixes userId spoofing concern):
                        // Even though orderId/paymentId/signature are hard to forge,
                        // this stops a logged-in user from verifying (and therefore
                        // triggering wallet credit + invoice mail) for an orderId
                        // that belongs to someone else's session/browser history.
                        // ─────────────────────────────────────────────────────────
                        PaymentTransaction existing = paymentTransactionRepository
                                        .findByOrderIdAndUserId(request.getRazorpayOrderId(), user.getId())
                                        .orElseThrow(() -> new RuntimeException(
                                                        "Order not found or does not belong to your account"));

                        if (!existing.getUserId().equals(user.getId())) {
                                log.warn("User {} attempted to verify order {} belonging to user {}",
                                                user.getId(), request.getRazorpayOrderId(), existing.getUserId());
                                throw new RuntimeException("This order does not belong to your account");
                        }

                        paymentSettlementService.settleCaptured(
                                        request.getRazorpayOrderId(), request.getRazorpayPaymentId());

                } catch (RuntimeException e) {
                        throw e;
                } catch (Exception e) {
                        throw new RuntimeException("Payment verification failed : " + e.getMessage());
                }
        }

        // ───────────────────────── WEBHOOK (source of truth, prevents fake/duplicate
        // payments) ─────────────────────────
        @Override
        public void handleWebhook(String payload, String signatureHeader) {

                try {
                        boolean valid = Utils.verifyWebhookSignature(payload, signatureHeader, webhookSecret);
                        if (!valid) {
                                log.warn("Webhook signature verification failed — possible spoofed request");
                                throw new RuntimeException("Invalid webhook signature");
                        }
                } catch (Exception e) {
                        throw new RuntimeException("Webhook signature verification error: " + e.getMessage());
                }

                JSONObject body = new JSONObject(payload);
                String event = body.optString("event");

                JSONObject paymentEntity = body
                                .optJSONObject("payload")
                                .optJSONObject("payment")
                                .optJSONObject("entity");

                if (paymentEntity == null)
                        return;

                String orderId = paymentEntity.optString("order_id");
                String paymentId = paymentEntity.optString("id");

                switch (event) {
                        case "payment.captured" -> paymentSettlementService.settleCaptured(orderId, paymentId);
                        case "payment.failed" -> {
                                String reason = paymentEntity.optString("error_description", "Payment failed");
                                paymentSettlementService.markFailed(orderId, reason, "failed");
                        }
                        default -> log.info("Unhandled webhook event: {}", event);
                }
        }

        // ───────────────────────── PAYMENT HISTORY ─────────────────────────
        @Override
        public List<PaymentHistoryResponse> getPaymentHistory() {

                User user = getAuthenticatedUser();

                // FIXED: same per-user rate limit key issue as the other methods.
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(
                                "payment-history:" + user.getId(),
                                rateLimitProperties.getPaymentHistory().getCapacity(),
                                rateLimitProperties.getPaymentHistory().getRefillTokens(),
                                rateLimitProperties.getPaymentHistory().getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {
                        throw new RuntimeException(rateLimitResponse.getMessage());
                }

                return paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                                .stream()
                                .map(payment -> PaymentHistoryResponse.builder()
                                                .orderId(payment.getOrderId())
                                                .paymentId(payment.getPaymentId())
                                                .amount(payment.getAmount())
                                                .currency(payment.getCurrency())
                                                .status(payment.getStatus())
                                                .gateway(payment.getGateway())
                                                .createdAt(payment.getCreatedAt())
                                                .updatedAt(payment.getUpdatedAt())
                                                .settledAt(payment.getSettledAt())
                                                .failureReason(payment.getFailureReason())
                                                .gatewayStatus(payment.getGatewayStatus())
                                                .subscriptionPlanId(payment.getSubscriptionPlanId())
                                                .originalAmount(payment.getOriginalAmount())
                                                .discountAmount(payment.getDiscountAmount())
                                                .build())
                                .toList();
        }

        @Override
        public void markPaymentCancelled(String orderId, String reason) {
                User user = getAuthenticatedUser();
                PaymentTransaction transaction = paymentTransactionRepository
                                .findByOrderIdAndUserId(orderId, user.getId())
                                .orElseThrow(() -> new RuntimeException(
                                                "Payment not found or does not belong to your account"));
                if (transaction.getStatus() == PaymentStatus.SUCCESS) {
                        return;
                }

                paymentSettlementService.markFailed(
                                orderId,
                                reason == null ? "Payment cancelled by user" : reason,
                                "client_cancelled");
                log.info("Payment {} marked as failed by the checkout client", orderId);
        }
}
