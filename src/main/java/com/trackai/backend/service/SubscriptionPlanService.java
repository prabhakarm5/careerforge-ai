package com.trackai.backend.service;

import com.trackai.backend.dto.payments.*;
import com.trackai.backend.dto.subscription.CreateSubscriptionPlanRequest;
import com.trackai.backend.dto.subscription.SubscriptionPlanResponse;

import java.util.List;

public interface SubscriptionPlanService {

        SubscriptionPlanResponse createPlan(
                        CreateSubscriptionPlanRequest request);

        List<SubscriptionPlanResponse> getAllPlans();

        SubscriptionPlanResponse getPlanById(
                        String planId);

        void deletePlan(
                        String planId);

        SubscriptionPlanResponse updatePlan(
                        String planId,
                        CreateSubscriptionPlanRequest request);
}