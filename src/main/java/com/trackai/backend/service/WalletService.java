package com.trackai.backend.service;

import com.trackai.backend.dto.WalletResponse;
import com.trackai.backend.dto.WalletTransactionResponse;
import com.trackai.backend.entity.SubscriptionPlan;
import com.trackai.backend.entity.Wallet;
import com.trackai.backend.enums.FeatureType;

import java.util.List;

public interface WalletService {

        // Create a new wallet for a user
        void createWallet(String userId);

        // Get the current wallet status for the logged-in user
        WalletResponse getCurrentWallet();

        // Get the wallet for a specific user by their ID
        Wallet getWalletByUserId(String userId);

        // Add tokens to a user's wallet for a specific feature type
        void addTokens(
                        String userId,
                        Long amount,
                        FeatureType featureType,
                        String description);

        // NEW — links a purchased/activated plan to the user's wallet,
        // credits the plan's tokens, and records which plan is active.
        void applyPlanToWallet(
                        String userId,
                        SubscriptionPlan plan);

        // Deduct tokens from a user's wallet for a specific feature type
        void consumeTokens(
                        String userId,
                        Long amount,
                        FeatureType featureType,
                        String description);

        // Check if a user has enough tokens for a specific feature type
        boolean hasEnoughTokens(
                        String userId,
                        Long amount);

        // Get the transaction history for a specific user
        List<WalletTransactionResponse> getTransactionHistory();

        // Check if the user has enough tokens for image generation
        void checkImageGenerationTokens();

        // Deduct tokens for image generation
        void consumeImageTokens(Long amount);

        // Refund tokens for image generation
        void refundImageTokens(Long amount);
}