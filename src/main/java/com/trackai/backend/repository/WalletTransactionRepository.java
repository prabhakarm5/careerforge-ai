package com.trackai.backend.repository;

import com.trackai.backend.entity.WalletTransaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository
                extends JpaRepository<WalletTransaction, String> {

        /*
         * ==========================================================
         * PostgreSQL ✅
         * MySQL ✅
         *
         * Ye repository PostgreSQL aur MySQL dono ke saath
         * fully compatible hai.
         *
         * Isme sirf Spring Data JPA Derived Query Methods use hue hain.
         * Koi bhi database-specific SQL ya Native Query nahi hai.
         *
         * MySQL ke liye bhi isi code ko use kar sakte ho.
         * Future me agar Native SQL (@Query(nativeQuery = true))
         * use karoge tab syntax alag hoga.
         * ==========================================================
         */

        // Transaction History of a User
        List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId);

}