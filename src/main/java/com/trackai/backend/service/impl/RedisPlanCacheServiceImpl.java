package com.trackai.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.dto.cache.CachedPlan;
import com.trackai.backend.service.RedisPlanCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPlanCacheServiceImpl
        implements RedisPlanCacheService {

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    /*
     * ==========================================================
     * Redis Keys
     * ==========================================================
     *
     * "plan_cache:all" -> saari plans ki list (JSON array)
     * "plan_cache:id:{id}" -> ek specific plan (JSON object)
     *
     * Do alag key patterns rakhne ki wajah:
     * - Public "/api/plans" list endpoint zyada hit hota hai
     * -> "all" key isko serve karegi
     * - "/api/plans/{id}" alag se hit ho sakta hai (jaise
     * checkout page pe ek specific plan dikhana)
     * -> "id:{id}" key isko serve karegi
     *
     * Dono independent cache hain isliye dono ko alag-alag
     * evict karna padta hai jab data change ho.
     */
    private static final String ALL_PLANS_KEY = "plan_cache:all";

    private static final String PLAN_BY_ID_PREFIX = "plan_cache:id:";

    /*
     * ==========================================================
     * Cache Expiry
     * ==========================================================
     *
     * Plans bahut kam change hote hain (admin ke through hi),
     * isliye lambi TTL (6 hours) rakhi hai. Chinta mat karo
     * stale data ki — kyunki har create/update/delete pe
     * hum explicitly evict kar denge (neeche dekho).
     *
     * TTL sirf ek SAFETY NET hai (agar evict call kabhi miss
     * ho jaaye kisi bug ki wajah se, to bhi 6 ghante mein
     * apne aap fresh ho jaayega).
     */
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    // ================= SAVE ALL PLANS =================
    @Override
    public void saveAllPlans(List<CachedPlan> plans) {

        try {

            String json = objectMapper.writeValueAsString(plans);

            redisTemplate.opsForValue().set(
                    ALL_PLANS_KEY,
                    json,
                    CACHE_TTL);

            log.info("All plans cached successfully : count={}", plans.size());

        } catch (JsonProcessingException e) {

            log.error("Failed to serialize plans list cache", e);

        } catch (Exception e) {

            log.error("Redis save failed (all plans)", e);
        }
    }

    // ================= GET ALL PLANS =================
    @Override
    public List<CachedPlan> getAllPlans() {

        try {

            String json = redisTemplate.opsForValue().get(ALL_PLANS_KEY);

            if (json == null) {

                log.info("Redis Cache MISS : {}", ALL_PLANS_KEY);

                return null;
            }

            log.info("Redis Cache HIT : {}", ALL_PLANS_KEY);

            return objectMapper.readValue(
                    json,
                    new TypeReference<List<CachedPlan>>() {
                    });

        } catch (Exception e) {

            log.error("Redis read failed (all plans)", e);

            return null;
        }
    }

    // ================= EVICT ALL PLANS =================
    @Override
    public void evictAllPlans() {

        try {

            redisTemplate.delete(ALL_PLANS_KEY);

            log.info("All plans cache evicted : {}", ALL_PLANS_KEY);

        } catch (Exception e) {

            log.error("Redis delete failed (all plans)", e);
        }
    }

    // ================= SAVE PLAN BY ID =================
    @Override
    public void savePlanById(CachedPlan plan) {

        if (plan == null || plan.getId() == null) {
            return;
        }

        try {

            String key = PLAN_BY_ID_PREFIX + plan.getId();

            String json = objectMapper.writeValueAsString(plan);

            redisTemplate.opsForValue().set(
                    key,
                    json,
                    CACHE_TTL);

            log.info("Plan cached successfully : {}", key);

        } catch (JsonProcessingException e) {

            log.error("Failed to serialize plan cache", e);

        } catch (Exception e) {

            log.error("Redis save failed (plan by id)", e);
        }
    }

    // ================= GET PLAN BY ID =================
    @Override
    public CachedPlan getPlanById(String planId) {

        try {

            String key = PLAN_BY_ID_PREFIX + planId;

            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {

                log.info("Redis Cache MISS : {}", key);

                return null;
            }

            log.info("Redis Cache HIT : {}", key);

            return objectMapper.readValue(
                    json,
                    CachedPlan.class);

        } catch (Exception e) {

            log.error("Redis read failed (plan by id)", e);

            return null;
        }
    }

    // ================= EVICT PLAN BY ID =================
    @Override
    public void evictPlanById(String planId) {

        try {

            String key = PLAN_BY_ID_PREFIX + planId;

            redisTemplate.delete(key);

            log.info("Plan cache evicted : {}", key);

        } catch (Exception e) {

            log.error("Redis delete failed (plan by id)", e);
        }
    }
}