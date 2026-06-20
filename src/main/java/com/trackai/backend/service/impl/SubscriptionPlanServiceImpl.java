package com.trackai.backend.service.impl;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.subscription.CreateSubscriptionPlanRequest;
import com.trackai.backend.dto.subscription.SubscriptionPlanResponse;
import com.trackai.backend.entity.SubscriptionPlan;
import com.trackai.backend.repository.SubscriptionPlanRepository;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.SubscriptionPlanService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanServiceImpl
                implements SubscriptionPlanService {

        private final SubscriptionPlanRepository subscriptionPlanRepository;

        private final RedisRateLimitService redisRateLimitService;

        private final RateLimitProperties rateLimitProperties;

        // CREATE PLAN
        @Override
        public SubscriptionPlanResponse createPlan(
                        CreateSubscriptionPlanRequest request) {

                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "create-plan",

                                rateLimitProperties
                                                .getCreatePlan()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getCreatePlan()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getCreatePlan()
                                                .getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                if (subscriptionPlanRepository
                                .findByName(request.getName())
                                .isPresent()) {

                        throw new RuntimeException(
                                        "Plan already exists");
                }

                SubscriptionPlan plan = SubscriptionPlan.builder()

                                .id(UUID.randomUUID().toString())

                                .name(request.getName())

                                .price(request.getPrice())

                                .tokens(request.getTokens())

                                .description(request.getDescription())

                                .active(true)

                                .createdAt(LocalDateTime.now())

                                .build();

                subscriptionPlanRepository.save(plan);

                return mapToResponse(plan);
        }

        // GET ALL PLANS
        @Override
        public List<SubscriptionPlanResponse> getAllPlans() {

                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "get-plans",

                                rateLimitProperties
                                                .getGetPlans()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getGetPlans()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getGetPlans()
                                                .getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                return subscriptionPlanRepository.findAll()

                                .stream()

                                .map(this::mapToResponse)

                                .toList();
        }

        // GET PLAN BY ID
        @Override
        public SubscriptionPlanResponse getPlanById(
                        String planId) {

                SubscriptionPlan plan = subscriptionPlanRepository

                                .findById(planId)

                                .orElseThrow(() ->

                                new RuntimeException(
                                                "Plan not found"));

                return mapToResponse(plan);
        }

        // UPDATE PLAN
        @Override
        public SubscriptionPlanResponse updatePlan(
                        String planId,
                        CreateSubscriptionPlanRequest request) {

                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "update-plan",

                                rateLimitProperties
                                                .getUpdatePlan()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getUpdatePlan()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getUpdatePlan()
                                                .getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                SubscriptionPlan plan = subscriptionPlanRepository

                                .findById(planId)

                                .orElseThrow(() ->

                                new RuntimeException(
                                                "Plan not found"));

                plan.setName(request.getName());

                plan.setPrice(request.getPrice());

                plan.setTokens(request.getTokens());

                plan.setDescription(request.getDescription());

                subscriptionPlanRepository.save(plan);

                return mapToResponse(plan);
        }

        // DELETE PLAN
        @Override
        public void deletePlan(
                        String planId) {

                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(

                                "delete-plan",

                                rateLimitProperties
                                                .getDeletePlan()
                                                .getCapacity(),

                                rateLimitProperties
                                                .getDeletePlan()
                                                .getRefillTokens(),

                                rateLimitProperties
                                                .getDeletePlan()
                                                .getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                SubscriptionPlan plan = subscriptionPlanRepository

                                .findById(planId)

                                .orElseThrow(() ->

                                new RuntimeException(
                                                "Plan not found"));

                subscriptionPlanRepository.delete(plan);
        }

        // DTO MAPPER
        private SubscriptionPlanResponse mapToResponse(
                        SubscriptionPlan plan) {

                return SubscriptionPlanResponse.builder()

                                .id(plan.getId())

                                .name(plan.getName())

                                .price(plan.getPrice())

                                .tokens(plan.getTokens())

                                .description(plan.getDescription())

                                .active(plan.getActive())

                                .build();
        }
}
