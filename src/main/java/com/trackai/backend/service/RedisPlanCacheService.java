package com.trackai.backend.service;

import com.trackai.backend.dto.cache.CachedPlan;

import java.util.List;

public interface RedisPlanCacheService {

    // Saare plans ki list cache karo (public listing ke liye)
    void saveAllPlans(List<CachedPlan> plans);

    // Cache se saari plans list nikalo (null agar cache MISS)
    List<CachedPlan> getAllPlans();

    // Saari plans wali list ka cache hatao (jab bhi koi plan
    // create/update/delete ho, is list ka data purana ho jaata hai)
    void evictAllPlans();

    // Ek specific plan ko cache mein daalo (planId se)
    void savePlanById(CachedPlan plan);

    // Ek specific plan ko cache se nikalo
    CachedPlan getPlanById(String planId);

    // Ek specific plan ka cache hatao (update/delete ke baad)
    void evictPlanById(String planId);
}