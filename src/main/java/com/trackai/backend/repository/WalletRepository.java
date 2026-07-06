package com.trackai.backend.repository;

import com.trackai.backend.entity.Wallet;

import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface WalletRepository
                extends JpaRepository<Wallet, String> {

        /*
         * ==========================================================
         * PostgreSQL ✅
         * MySQL ✅
         *
         * Ye repository PostgreSQL aur MySQL dono ke saath
         * fully compatible hai.
         *
         * Koi bhi database-specific SQL query use nahi hui hai.
         *
         * NOTE:
         * Agar future me native SQL (@Query(nativeQuery = true))
         * use karoge tab PostgreSQL aur MySQL ke syntax alag honge.
         * Derived Query Methods (findBy...) dono databases me
         * same tarah kaam karte hain.
         * ==========================================================
         */

        // Find Wallet by User ID
        Optional<Wallet> findByUserId(String userId);

        // ✅ NEW: Locked read — MUST be used inside a @Transactional method
        // whenever wallet balance is being modified (addTokens, consumeTokens).
        // This takes a DB-level row lock (SELECT ... FOR UPDATE) so that
        // concurrent requests (e.g. webhook + client verify hitting at the
        // same time) cannot both read the same stale balance and overwrite
        // each other's update (lost-update / race condition).
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
        Optional<Wallet> findByUserIdForUpdate(@Param("userId") String userId);

}