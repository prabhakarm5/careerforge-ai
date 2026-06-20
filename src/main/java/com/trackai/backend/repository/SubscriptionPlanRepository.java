package com.trackai.backend.repository;

import com.trackai.backend.entity.SubscriptionPlan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionPlanRepository
                extends JpaRepository<SubscriptionPlan, String> {

        // Find plan by name
        Optional<SubscriptionPlan> findByName(
                        String name);
}