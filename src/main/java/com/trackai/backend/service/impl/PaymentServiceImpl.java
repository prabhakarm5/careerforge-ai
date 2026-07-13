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
import com.trackai.backend.service.MailService;
import com.trackai.backend.service.PaymentService;
import com.trackai.backend.service.PdfInvoiceService;
import com.trackai.backend.service.PromoCodeService;
import com.trackai.backend.service.PromoCodeService.PromoApplication;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.WalletService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
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
        private final WalletService walletService;
        private final RedisRateLimitService redisRateLimitService;
        private final RateLimitProperties rateLimitProperties;
        private final MailService mailService;
        private final PdfInvoiceService pdfInvoiceService;
        private final PromoCodeService promoCodeService;

        @Value("${razorpay.key-id}")
        private String keyId;

        @Value("${razorpay.key-secret}")
        private String keySecret;

        @Value("${razorpay.webhook-secret}")
        private String webhookSecret;

        // Refund SLA shown to the user in the failure email — keep this in one
        // place so the message and any future logic stay in sync.
        private static final int REFUND_SLA_DAYS = 7;

        private User getAuthenticatedUser() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                String email = authentication.getName();
                return userRepository.findByEmail(email)
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
        @Transactional
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
                                markFailed(request.getRazorpayOrderId(), "Signature mismatch");
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
                                        .findByOrderId(request.getRazorpayOrderId())
                                        .orElseThrow(() -> new RuntimeException("Order not found"));

                        if (!existing.getUserId().equals(user.getId())) {
                                log.warn("User {} attempted to verify order {} belonging to user {}",
                                                user.getId(), request.getRazorpayOrderId(), existing.getUserId());
                                throw new RuntimeException("This order does not belong to your account");
                        }

                        processSuccessfulPayment(request.getRazorpayOrderId(), request.getRazorpayPaymentId());

                } catch (RuntimeException e) {
                        throw e;
                } catch (Exception e) {
                        throw new RuntimeException("Payment verification failed : " + e.getMessage());
                }
        }

        // ───────────────────────── WEBHOOK (source of truth, prevents fake/duplicate
        // payments) ─────────────────────────
        @Transactional
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
                        case "payment.captured" -> processSuccessfulPayment(orderId, paymentId);
                        case "payment.failed" -> {
                                String reason = paymentEntity.optString("error_description", "Payment failed");
                                markFailed(orderId, reason);
                        }
                        default -> log.info("Unhandled webhook event: {}", event);
                }
        }

        // ───────────────────────── SHARED IDEMPOTENT SUCCESS HANDLER
        // ─────────────────────────
        private void processSuccessfulPayment(String orderId, String paymentId) {

                PaymentTransaction paymentTransaction = paymentTransactionRepository.findByOrderId(orderId)
                                .orElseThrow(() -> new RuntimeException("Payment not found for order " + orderId));

                // idempotency: already processed (either by client verify or webhook) -> no-op
                if (paymentTransaction.getStatus() == PaymentStatus.SUCCESS) {
                        log.info("Payment {} already processed, skipping duplicate", orderId);
                        return;
                }

                if (paymentTransaction.getStatus() == PaymentStatus.SUCCESS) {
                        log.info("Already processed");
                        return;
                }

                if (paymentTransaction.getStatus() == PaymentStatus.FAILED) {
                        log.warn("Ignoring success because payment already marked failed");
                        return;
                }

                SubscriptionPlan plan = subscriptionPlanRepository.findById(paymentTransaction.getSubscriptionPlanId())
                                .orElseThrow(() -> new RuntimeException("Plan not found"));

                paymentTransaction.setPaymentId(paymentId);
                paymentTransaction.setStatus(PaymentStatus.SUCCESS);
                paymentTransaction.setUpdatedAt(LocalDateTime.now());

                try {
                        paymentTransactionRepository.save(paymentTransaction);
                } catch (DataIntegrityViolationException e) {
                        // paymentId unique constraint hit -> this paymentId already used for another
                        // row = fraud/duplicate attempt
                        log.error("Duplicate paymentId insert blocked: {}", paymentId);
                        return;
                }

                walletService.applyPlanToWallet(
                                paymentTransaction.getUserId(),
                                plan);

                // ─────────────────────────────────────────────────────────
                // MAIL IS FIRE-AND-FORGET FROM HERE:
                // Wallet credit (the thing that actually matters to the user)
                // is already committed above. Building the PDF + sending mail
                // is slow (SMTP round-trip) and must NEVER block or fail the
                // payment transaction — so it's handed off to an async method.
                // If mail sending throws, it's caught/logged inside that method
                // and never bubbles back here.
                // ─────────────────────────────────────────────────────────
                User user = userRepository.findById(paymentTransaction.getUserId()).orElse(null);
                if (user != null) {
                        sendSuccessMailAsync(user.getName(), user.getEmail(), paymentTransaction, plan);
                }
        }

        // Runs on a separate thread (see AsyncConfig) so verifyPayment()/webhook
        // response returns immediately after the DB commit, without waiting on
        // PDF generation + SMTP.
        @Async
        public void sendSuccessMailAsync(String userName, String userEmail,
                        PaymentTransaction paymentTransaction, SubscriptionPlan plan) {
                try {
                        byte[] invoicePdf = pdfInvoiceService.generateInvoice(
                                        // re-fetch not needed — passed in already
                                        userRepository.findById(paymentTransaction.getUserId()).orElseThrow(),
                                        paymentTransaction, plan, true, null);

                        mailService.sendPaymentSuccessEmail(userName, userEmail, paymentTransaction, invoicePdf);

                } catch (Exception e) {
                        // Never let a mail failure look like a payment failure —
                        // just log it so it can be investigated/resent manually.
                        log.error("Failed to send success email for order {}: {}",
                                        paymentTransaction.getOrderId(), e.getMessage());
                }
        }

        private void markFailed(String orderId, String reason) {

                paymentTransactionRepository.findByOrderId(orderId).ifPresent(paymentTransaction -> {

                        if (paymentTransaction.getStatus() == PaymentStatus.SUCCESS) {
                                return;
                        }

                        if (paymentTransaction.getStatus() == PaymentStatus.FAILED) {
                                return;
                        }

                        if (paymentTransaction.getStatus() == PaymentStatus.SUCCESS)
                                return; // never downgrade a success

                        paymentTransaction.setStatus(PaymentStatus.FAILED);
                        paymentTransaction.setFailureReason(reason);
                        paymentTransaction.setUpdatedAt(LocalDateTime.now());
                        paymentTransactionRepository.save(paymentTransaction);

                        User user = userRepository.findById(paymentTransaction.getUserId()).orElse(null);
                        SubscriptionPlan plan = subscriptionPlanRepository
                                        .findById(paymentTransaction.getSubscriptionPlanId()).orElse(null);

                        if (user != null && plan != null) {
                                // Failure mail is async too — same reasoning as success mail.
                                sendFailedMailAsync(user.getName(), user.getEmail(), paymentTransaction, plan, reason);
                        }
                });
        }

        @Async
        public void sendFailedMailAsync(String userName, String userEmail,
                        PaymentTransaction paymentTransaction, SubscriptionPlan plan, String reason) {
                try {
                        byte[] invoicePdf = pdfInvoiceService.generateInvoice(
                                        userRepository.findById(paymentTransaction.getUserId()).orElseThrow(),
                                        paymentTransaction, plan, false, reason);

                        mailService.sendPaymentFailedEmail(userName, userEmail, paymentTransaction, reason,
                                        invoicePdf, REFUND_SLA_DAYS);

                } catch (Exception e) {
                        log.error("Failed to send failure email for order {}: {}",
                                        paymentTransaction.getOrderId(), e.getMessage());
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
                                                .build())
                                .toList();
        }

        @Override
        @Transactional
        public void markPaymentCancelled(String orderId, String reason) {

                PaymentTransaction paymentTransaction = paymentTransactionRepository
                                .findByOrderId(orderId)
                                .orElseThrow(() -> new RuntimeException("Payment not found"));

                // already successful payment ko kabhi change mat karo
                if (paymentTransaction.getStatus() == PaymentStatus.SUCCESS) {
                        return;
                }

                paymentTransaction.setStatus(PaymentStatus.FAILED);
                paymentTransaction.setFailureReason(reason);
                paymentTransaction.setUpdatedAt(LocalDateTime.now());

                paymentTransactionRepository.save(paymentTransaction);

                User user = userRepository
                                .findById(paymentTransaction.getUserId())
                                .orElse(null);

                SubscriptionPlan plan = subscriptionPlanRepository
                                .findById(paymentTransaction.getSubscriptionPlanId())
                                .orElse(null);

                if (user != null && plan != null) {

                        sendFailedMailAsync(
                                        user.getName(),
                                        user.getEmail(),
                                        paymentTransaction,
                                        plan,
                                        reason);
                }

                log.info("Payment {} marked as FAILED : {}", orderId, reason);
        }
}