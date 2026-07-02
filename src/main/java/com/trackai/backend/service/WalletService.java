package com.trackai.backend.service;

import com.trackai.backend.dto.WalletResponse;
import com.trackai.backend.dto.WalletTransactionResponse;
import com.trackai.backend.entity.Wallet;
import com.trackai.backend.enums.FeatureType;

import java.util.List;

public interface WalletService {

        // Create wallet for new user
        void createWallet(
                        String userId);

        // Get current user's wallet
        WalletResponse getCurrentWallet();

        // Add tokens
        void addTokens(
                        String userId,
                        Long amount,
                        FeatureType featureType,
                        String description);

        // Consume tokens
        void consumeTokens(
                        String userId,
                        Long amount,
                        FeatureType featureType,
                        String description);

        // Check token availability
        boolean hasEnoughTokens(
                        String userId,
                        Long amount);

        // Transaction history
        List<WalletTransactionResponse> getTransactionHistory();

        // IMAGE
        void checkImageGenerationTokens();

        void consumeImageTokens(Long amount);

        void refundImageTokens(Long amount);

        // for getWalletByUserId
        Wallet getWalletByUserId(String userId);
}