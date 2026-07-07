package com.trackai.backend.service.impl;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.cache.CachedPlan;
import com.trackai.backend.dto.subscription.CreateSubscriptionPlanRequest;
import com.trackai.backend.dto.subscription.SubscriptionPlanResponse;
import com.trackai.backend.entity.SubscriptionPlan;
import com.trackai.backend.repository.SubscriptionPlanRepository;
import com.trackai.backend.service.RedisPlanCacheService;
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

        // FIX: naya dependency — plans ke liye dedicated cache service
        private final RedisPlanCacheService redisPlanCacheService;

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

                /*
                 * FIX — CACHE INVALIDATION (CREATE):
                 * Naya plan DB mein aa gaya hai. Ab jo "all plans" list
                 * Redis mein cached thi, wo OUTDATED ho gayi hai (usme
                 * ye naya plan nahi hai). Isliye us list cache ko
                 * EVICT karna zaroori hai.
                 *
                 * Agla GET /api/plans call cache MISS karega, fresh
                 * data DB se lega (jisme naya plan bhi included hoga),
                 * aur cache ko dobara populate kar dega.
                 *
                 * Individual "plan_cache:id:{id}" ko yahan save karne
                 * ki zarurat nahi — wo lazily tab banega jab koi
                 * getPlanById() call karega.
                 */
                redisPlanCacheService.evictAllPlans();

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

                /*
                 * STEP-1: REDIS CHECK
                 * Pehle cache mein dekho. Agar mil gaya to seedha wahi
                 * return karo — DB tak jaana hi nahi padega.
                 */
                List<CachedPlan> cachedPlans = redisPlanCacheService.getAllPlans();

                if (cachedPlans != null) {

                        return cachedPlans.stream()
                                        .map(this::mapCachedToResponse)
                                        .toList();
                }

                /*
                 * STEP-2: DATABASE (cache MISS case)
                 */
                List<SubscriptionPlan> plans = subscriptionPlanRepository.findAll();

                List<SubscriptionPlanResponse> response = plans.stream()
                                .map(this::mapToResponse)
                                .toList();

                /*
                 * STEP-3: SAVE CACHE
                 * Fresh data DB se mila, ab isse Redis mein daal do
                 * taaki agli request cache se hi serve ho jaaye.
                 */
                List<CachedPlan> toCache = plans.stream()
                                .map(this::mapToCached)
                                .toList();

                redisPlanCacheService.saveAllPlans(toCache);

                return response;
        }

        // GET PLAN BY ID
        @Override
        public SubscriptionPlanResponse getPlanById(
                        String planId) {

                /*
                 * STEP-1: REDIS CHECK
                 */
                CachedPlan cachedPlan = redisPlanCacheService.getPlanById(planId);

                if (cachedPlan != null) {

                        return mapCachedToResponse(cachedPlan);
                }

                /*
                 * STEP-2: DATABASE
                 */
                SubscriptionPlan plan = subscriptionPlanRepository

                                .findById(planId)

                                .orElseThrow(() ->

                                new RuntimeException(
                                                "Plan not found"));

                /*
                 * STEP-3: SAVE CACHE
                 */
                redisPlanCacheService.savePlanById(mapToCached(plan));

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

                /*
                 * FIX — CACHE INVALIDATION (UPDATE):
                 * Do jagah data stale ho gaya hai:
                 *
                 * 1. "all plans" list cache -> isme is plan ka PURANA
                 * (price/tokens/description) data hai -> EVICT karo
                 *
                 * 2. "plan_cache:id:{planId}" -> yahan bhi purana data
                 * hai -> EVICT karo (evict kar rahe hain, save nahi,
                 * kyunki agli read pe fresh data DB se aa ke khud
                 * cache ho jaayega — evict simpler aur safer hai
                 * save-with-new-data se, kam chance of bugs)
                 */
                redisPlanCacheService.evictAllPlans();

                redisPlanCacheService.evictPlanById(planId);

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

                /*
                 * FIX — CACHE INVALIDATION (DELETE):
                 * Plan DB se gaya, isliye:
                 * 1. "all plans" list -> isme deleted plan abhi bhi
                 * dikhega jab tak evict na karo
                 * 2. Individual "plan_cache:id:{planId}" -> ye ab
                 * "ghost" cache ban jaayega (DB mein exist hi nahi
                 * karta), isliye evict zaroori hai warna koi
                 * getPlanById() call ek deleted plan return kar
                 * dega DB check kiye bina
                 */
                redisPlanCacheService.evictAllPlans();

                redisPlanCacheService.evictPlanById(planId);
        }

        // ============== MAPPERS ==============

        // Entity -> Response DTO
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

        // Entity -> Cache DTO
        private CachedPlan mapToCached(
                        SubscriptionPlan plan) {

                return CachedPlan.builder()

                                .id(plan.getId())

                                .name(plan.getName())

                                .price(plan.getPrice())

                                .tokens(plan.getTokens())

                                .description(plan.getDescription())

                                .active(plan.getActive())

                                .createdAt(plan.getCreatedAt())

                                .build();
        }

        // Cache DTO -> Response DTO
        private SubscriptionPlanResponse mapCachedToResponse(
                        CachedPlan cachedPlan) {

                return SubscriptionPlanResponse.builder()

                                .id(cachedPlan.getId())

                                .name(cachedPlan.getName())

                                .price(cachedPlan.getPrice())

                                .tokens(cachedPlan.getTokens())

                                .description(cachedPlan.getDescription())

                                .active(cachedPlan.getActive())

                                .build();
        }
}