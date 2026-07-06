package com.trackai.backend.repository;

import com.trackai.backend.entity.SubscriptionPlan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionPlanRepository
                extends JpaRepository<SubscriptionPlan, String> {

        /*
         * ==========================================================
         * PostgreSQL ✅
         * MySQL ✅
         *
         * Ye repository PostgreSQL aur MySQL dono ke saath
         * fully compatible hai.
         *
         * Koi database-specific query nahi hai.
         * Future me agar native SQL use karoge tab hi
         * PostgreSQL/MySQL alag handling ki zarurat padegi.
         * ==========================================================
         */

        // Find Subscription Plan by Name
        Optional<SubscriptionPlan> findByName(String name);

}