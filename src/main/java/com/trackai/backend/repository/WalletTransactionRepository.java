package com.trackai.backend.repository;

import com.trackai.backend.entity.WalletTransaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository
                extends JpaRepository<WalletTransaction, String> {

        // Transaction history of a user
        List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(
                        String userId);
}