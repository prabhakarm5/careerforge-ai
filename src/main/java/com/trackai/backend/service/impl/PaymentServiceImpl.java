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
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.enums.PaymentGateway;
import com.trackai.backend.enums.PaymentStatus;
import com.trackai.backend.repository.PaymentTransactionRepository;
import com.trackai.backend.repository.SubscriptionPlanRepository;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.PaymentService;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.WalletService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl
                implements PaymentService {

        private final UserRepository userRepository;

        private final SubscriptionPlanRepository subscriptionPlanRepository;

        private final PaymentTransactionRepository paymentTransactionRepository;

        private final RazorpayClient razorpayClient;

        private final WalletService walletService;

        private final RedisRateLimitService redisRateLimitService;

        private final RateLimitProperties rateLimitProperties;

        @Value("${razorpay.key-id}")
        private String keyId;

        @Value("${razorpay.key-secret}")
        private String keySecret;

        // CURRENT USER
        private User getAuthenticatedUser() {

                Authentication authentication = SecurityContextHolder
                                .getContext()
                                .getAuthentication();

                String email = authentication.getName();

                return userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found"));
        }

        // CREATE ORDER
        @Override
        public CreateOrderResponse createOrder(
                        CreateOrderRequest request) {
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "create-order",

                                rateLimitProperties
                                                .getCreateOrder()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getCreateOrder()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getCreateOrder()
                                                .getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                User user = getAuthenticatedUser();

                SubscriptionPlan plan = subscriptionPlanRepository
                                .findById(request.getPlanId())
                                .orElseThrow(() -> new RuntimeException(
                                                "Plan not found"));

                try {

                        JSONObject options = new JSONObject();

                        options.put(
                                        "amount",
                                        plan.getPrice() * 100);

                        options.put(
                                        "currency",
                                        "INR");

                        options.put(
                                        "receipt",
                                        UUID.randomUUID().toString());

                        Order order = razorpayClient.orders
                                        .create(options);

                        String orderId = order.get("id")
                                        .toString();

                        PaymentTransaction payment = PaymentTransaction.builder()

                                        .id(UUID.randomUUID().toString())

                                        .userId(user.getId())

                                        .subscriptionPlanId(plan.getId())

                                        .orderId(orderId)

                                        .amount(plan.getPrice())

                                        .currency("INR")

                                        .status(
                                                        PaymentStatus.PENDING)

                                        .gateway(
                                                        PaymentGateway.RAZORPAY)

                                        .createdAt(
                                                        LocalDateTime.now())

                                        .build();

                        paymentTransactionRepository
                                        .save(payment);

                        return CreateOrderResponse
                                        .builder()

                                        .orderId(orderId)

                                        .amount(
                                                        plan.getPrice())

                                        .currency("INR")

                                        .keyId(keyId)

                                        .build();

                } catch (Exception e) {

                        throw new RuntimeException(
                                        "Failed to create order : "
                                                        + e.getMessage());
                }
        }

        // VERIFY PAYMENT
        @Transactional
        @Override
        public void verifyPayment(
                        VerifyPaymentRequest request) {

                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "verify-payment",

                                rateLimitProperties
                                                .getVerifyPayment()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getVerifyPayment()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getVerifyPayment()
                                                .getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                try {

                        JSONObject attributes = new JSONObject();

                        attributes.put(
                                        "razorpay_order_id",
                                        request.getRazorpayOrderId());

                        attributes.put(
                                        "razorpay_payment_id",
                                        request.getRazorpayPaymentId());

                        attributes.put(
                                        "razorpay_signature",
                                        request.getRazorpaySignature());

                        boolean verified = Utils.verifyPaymentSignature(
                                        attributes,
                                        keySecret);

                        if (!verified) {

                                throw new RuntimeException(
                                                "Invalid payment signature");
                        }

                        PaymentTransaction paymentTransaction = paymentTransactionRepository
                                        .findByOrderId(
                                                        request.getRazorpayOrderId())
                                        .orElseThrow(
                                                        () -> new RuntimeException(
                                                                        "Payment not found"));

                        // Already processed
                        if (paymentTransaction.getStatus() == PaymentStatus.SUCCESS) {

                                return;
                        }

                        // Update payment transaction
                        SubscriptionPlan plan = subscriptionPlanRepository
                                        .findById(
                                                        paymentTransaction
                                                                        .getSubscriptionPlanId())
                                        .orElseThrow(
                                                        () -> new RuntimeException(
                                                                        "Plan not found"));

                        paymentTransaction.setPaymentId(
                                        request.getRazorpayPaymentId());

                        paymentTransaction.setStatus(
                                        PaymentStatus.SUCCESS);

                        paymentTransactionRepository.save(
                                        paymentTransaction);

                        // Add tokens to wallet

                        walletService.addTokens(

                                        paymentTransaction.getUserId(),

                                        plan.getTokens(),

                                        FeatureType.SUBSCRIPTION,

                                        "Subscription purchase");

                }

                catch (Exception e) {

                        throw new RuntimeException(
                                        "Payment verification failed : "
                                                        + e.getMessage());
                }
        }

        // PAYMENT HISTORY
        @Override
        public List<PaymentHistoryResponse> getPaymentHistory() {
                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "payment-history",

                                rateLimitProperties
                                                .getPaymentHistory()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getPaymentHistory()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getPaymentHistory()
                                                .getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                User user = getAuthenticatedUser();

                return paymentTransactionRepository

                                .findByUserIdOrderByCreatedAtDesc(
                                                user.getId())

                                .stream()

                                .map(payment ->

                                PaymentHistoryResponse.builder()

                                                .orderId(
                                                                payment.getOrderId())

                                                .paymentId(
                                                                payment.getPaymentId())

                                                .amount(
                                                                payment.getAmount())

                                                .currency(
                                                                payment.getCurrency())

                                                .status(
                                                                payment.getStatus())

                                                .gateway(
                                                                payment.getGateway())

                                                .createdAt(
                                                                payment.getCreatedAt())

                                                .build())

                                .toList();
        }
}