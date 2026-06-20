package com.trackai.backend.controller;

import com.trackai.backend.dto.payments.CreateOrderRequest;
import com.trackai.backend.dto.payments.CreateOrderResponse;
import com.trackai.backend.dto.payments.PaymentHistoryResponse;
import com.trackai.backend.dto.payments.VerifyPaymentRequest;
import com.trackai.backend.service.PaymentService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

        private final PaymentService paymentService;

        // CREATE ORDER
        @PostMapping("/create-order")
        public ResponseEntity<CreateOrderResponse> createOrder(

                        @Valid @RequestBody CreateOrderRequest request) {

                return ResponseEntity.ok(

                                paymentService.createOrder(
                                                request));
        }

        // VERIFY PAYMENT
        @PostMapping("/verify")
        public ResponseEntity<Map<String, String>> verifyPayment(

                        @Valid @RequestBody VerifyPaymentRequest request) {

                paymentService.verifyPayment(
                                request);

                return ResponseEntity.ok(

                                Map.of(
                                                "message",
                                                "Payment verified successfully"));
        }

        // PAYMENT HISTORY
        @GetMapping("/history")
        public ResponseEntity<List<PaymentHistoryResponse>> getPaymentHistory() {

                return ResponseEntity.ok(

                                paymentService.getPaymentHistory());
        }
}