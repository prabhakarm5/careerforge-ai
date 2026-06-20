package com.trackai.backend.controller;

import com.trackai.backend.dto.subscription.CreateSubscriptionPlanRequest;
import com.trackai.backend.dto.subscription.SubscriptionPlanResponse;
import com.trackai.backend.service.SubscriptionPlanService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubscriptionPlanController {

        private final SubscriptionPlanService subscriptionPlanService;

        // CREATE PLAN (ADMIN)
        @PostMapping("/admin/plans")
        public ResponseEntity<SubscriptionPlanResponse> createPlan(

                        @Valid @RequestBody CreateSubscriptionPlanRequest request) {

                return ResponseEntity.ok(

                                subscriptionPlanService.createPlan(
                                                request));
        }

        // GET ALL PLANS (PUBLIC)
        @GetMapping("/plans")
        public ResponseEntity<List<SubscriptionPlanResponse>> getAllPlans() {

                return ResponseEntity.ok(

                                subscriptionPlanService.getAllPlans());
        }

        // GET PLAN BY ID (PUBLIC)
        @GetMapping("/plans/{planId}")
        public ResponseEntity<SubscriptionPlanResponse> getPlanById(

                        @PathVariable String planId) {

                return ResponseEntity.ok(

                                subscriptionPlanService.getPlanById(
                                                planId));
        }

        // UPDATE PLAN (ADMIN)
        @PutMapping("/admin/plans/{planId}")
        public ResponseEntity<SubscriptionPlanResponse> updatePlan(

                        @PathVariable String planId,

                        @Valid @RequestBody CreateSubscriptionPlanRequest request) {

                return ResponseEntity.ok(

                                subscriptionPlanService.updatePlan(
                                                planId,
                                                request));
        }

        // DELETE PLAN (ADMIN)
        @DeleteMapping("/admin/plans/{planId}")
        public ResponseEntity<Map<String, String>> deletePlan(

                        @PathVariable String planId) {

                subscriptionPlanService.deletePlan(
                                planId);

                return ResponseEntity.ok(

                                Map.of(
                                                "message",
                                                "Subscription plan deleted successfully"));
        }
}