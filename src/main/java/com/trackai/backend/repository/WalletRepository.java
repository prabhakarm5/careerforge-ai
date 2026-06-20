package com.trackai.backend.repository;

import com.trackai.backend.entity.Wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository
                extends JpaRepository<Wallet, String> {

        // Find wallet by user id
        Optional<Wallet> findByUserId(
                        String userId);
}