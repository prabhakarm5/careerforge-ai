package com.trackai.backend.service.impl;

import com.trackai.backend.dto.WalletResponse;
import com.trackai.backend.dto.WalletTransactionResponse;
import com.trackai.backend.entity.SubscriptionPlan;
import com.trackai.backend.entity.User;
import com.trackai.backend.entity.Wallet;
import com.trackai.backend.entity.WalletTransaction;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.enums.TransactionType;
import com.trackai.backend.exception.InsufficientTokensException;
import com.trackai.backend.exception.WalletAlreadyExistsException;
import com.trackai.backend.exception.WalletNotFoundException;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.repository.WalletRepository;
import com.trackai.backend.repository.WalletTransactionRepository;
import com.trackai.backend.service.WalletService;

import jakarta.transaction.Transactional;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl
                implements WalletService {

        private final WalletRepository walletRepository;

        private final WalletTransactionRepository walletTransactionRepository;

        private final UserRepository userRepository;

        @Value("${app.wallet.initial-tokens}")
        private Long initialTokens;

        @Value("${trackai.tokens.image}")
        private Long imageTokens;

        // Get authenticated user
        private User getAuthenticatedUser() {

                Authentication authentication = SecurityContextHolder
                                .getContext()
                                .getAuthentication();

                String email = authentication.getName();

                return userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "Authenticated user not found"));
        }

        // Create wallet
        @Override
        public void createWallet(String userId) {
                if (walletRepository.findByUserId(userId).isPresent()) {
                        throw new WalletAlreadyExistsException(
                                        "Wallet already exists");
                }

                Wallet wallet = Wallet.builder()
                                .id(UUID.randomUUID().toString())
                                .userId(userId)
                                .totalTokens(initialTokens)
                                .usedTokens(0L)
                                .remainingTokens(initialTokens)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                walletRepository.save(wallet);

                WalletTransaction transaction = WalletTransaction.builder()
                                .id(UUID.randomUUID().toString())
                                .userId(userId)
                                .amount(initialTokens)
                                .transactionType(TransactionType.CREDIT)
                                .featureType(FeatureType.BONUS)
                                .description("Welcome bonus tokens")
                                .createdAt(LocalDateTime.now())
                                .build();

                walletTransactionRepository.save(transaction);
        }

        // Get current wallet (via SecurityContext — only safe on request thread)
        // FIXED: now returns currentPlanId / currentPlanName so the frontend
        // can actually show which plan the user has, instead of always
        // falling back to "Free".
        @Override
        public WalletResponse getCurrentWallet() {

                User user = getAuthenticatedUser();

                Wallet wallet = walletRepository
                                .findByUserId(user.getId())
                                .orElseThrow(() -> new WalletNotFoundException(
                                                "Wallet not found"));

                return WalletResponse.builder()
                                .totalTokens(wallet.getTotalTokens())
                                .usedTokens(wallet.getUsedTokens())
                                .remainingTokens(wallet.getRemainingTokens())
                                .currentPlanId(wallet.getCurrentPlanId())
                                .currentPlanName(wallet.getCurrentPlanName())
                                .build();
        }

        // Get wallet by explicit userId — SAFE to call from ANY thread
        @Override
        public Wallet getWalletByUserId(String userId) {
                return walletRepository
                                .findByUserId(userId)
                                .orElseThrow(() -> new WalletNotFoundException(
                                                "Wallet not found"));
        }

        // Add tokens — row-level DB lock to prevent lost-update race condition
        // (e.g. webhook + client verify racing on the same payment, or two
        // parallel refund calls). @Transactional is REQUIRED here — the lock
        // is held only for the duration of the transaction; without it, the
        // lock has no effect.
        @Transactional
        @Override
        public void addTokens(
                        String userId,
                        Long amount,
                        FeatureType featureType,
                        String description) {

                Wallet wallet = walletRepository
                                .findByUserIdForUpdate(userId)
                                .orElseThrow(() -> new WalletNotFoundException(
                                                "Wallet not found"));

                wallet.setTotalTokens(
                                wallet.getTotalTokens() + amount);

                wallet.setRemainingTokens(
                                wallet.getRemainingTokens() + amount);

                wallet.setUpdatedAt(LocalDateTime.now());

                walletRepository.save(wallet);

                WalletTransaction transaction = WalletTransaction.builder()
                                .id(UUID.randomUUID().toString())
                                .userId(userId)
                                .amount(amount)
                                .transactionType(TransactionType.CREDIT)
                                .featureType(featureType)
                                .description(description)
                                .createdAt(LocalDateTime.now())
                                .build();

                walletTransactionRepository.save(transaction);
        }

        // FIXED: applyPlanToWallet is now a proper interface method —
        // @Override added, @Transactional added, and it uses the SAME
        // row-level lock (findByUserIdForUpdate) as addTokens/consumeTokens.
        // Without the lock, a free-plan-activate call racing with a payment
        // webhook credit could overwrite each other's token totals.
        // It also now writes a WalletTransaction row so the plan purchase
        // shows up in transaction history — previously it silently updated
        // the wallet with zero audit trail.
        @Transactional
        @Override
        public void applyPlanToWallet(String userId, SubscriptionPlan plan) {

                Wallet wallet = walletRepository
                                .findByUserIdForUpdate(userId)
                                .orElseThrow(() -> new WalletNotFoundException(
                                                "Wallet not found"));

                wallet.setTotalTokens(
                                wallet.getTotalTokens() + plan.getTokens());

                wallet.setRemainingTokens(
                                wallet.getRemainingTokens() + plan.getTokens());

                wallet.setCurrentPlanId(plan.getId());

                wallet.setCurrentPlanName(plan.getName());

                wallet.setUpdatedAt(LocalDateTime.now());

                walletRepository.save(wallet);

                WalletTransaction transaction = WalletTransaction.builder()
                                .id(UUID.randomUUID().toString())
                                .userId(userId)
                                .amount(plan.getTokens())
                                .transactionType(TransactionType.CREDIT)
                                .featureType(FeatureType.SUBSCRIPTION)
                                .description(plan.getName() + " plan activated")
                                .createdAt(LocalDateTime.now())
                                .build();

                walletTransactionRepository.save(transaction);
        }

        // Consume tokens — same locked-read fix, prevents balance going
        // negative when two parallel consume requests race each other.
        @Transactional
        @Override
        public void consumeTokens(
                        String userId,
                        Long amount,
                        FeatureType featureType,
                        String description) {

                Wallet wallet = walletRepository
                                .findByUserIdForUpdate(userId)
                                .orElseThrow(() -> new WalletNotFoundException(
                                                "Wallet not found"));

                if (wallet.getRemainingTokens() < amount) {
                        throw new InsufficientTokensException(
                                        "Insufficient tokens");
                }

                wallet.setUsedTokens(
                                wallet.getUsedTokens() + amount);

                wallet.setRemainingTokens(
                                wallet.getRemainingTokens() - amount);

                wallet.setUpdatedAt(LocalDateTime.now());

                walletRepository.save(wallet);

                WalletTransaction transaction = WalletTransaction.builder()
                                .id(UUID.randomUUID().toString())
                                .userId(userId)
                                .amount(amount)
                                .transactionType(TransactionType.DEBIT)
                                .featureType(featureType)
                                .description(description)
                                .createdAt(LocalDateTime.now())
                                .build();

                walletTransactionRepository.save(transaction);
        }

        // Check token balance — plain read, no lock needed (informational only)
        @Override
        public boolean hasEnoughTokens(
                        String userId,
                        Long amount) {

                Wallet wallet = walletRepository
                                .findByUserId(userId)
                                .orElseThrow(() -> new WalletNotFoundException(
                                                "Wallet not found"));

                return wallet.getRemainingTokens() >= amount;
        }

        // Transaction history
        @Override
        public List<WalletTransactionResponse> getTransactionHistory() {

                User user = getAuthenticatedUser();

                return walletTransactionRepository
                                .findByUserIdOrderByCreatedAtDesc(user.getId())
                                .stream()
                                .map(transaction -> WalletTransactionResponse.builder()
                                                .amount(transaction.getAmount())
                                                .transactionType(transaction.getTransactionType())
                                                .featureType(transaction.getFeatureType())
                                                .description(transaction.getDescription())
                                                .createdAt(transaction.getCreatedAt())
                                                .build())
                                .toList();
        }

        @Override
        public void checkImageGenerationTokens() {

                User user = getAuthenticatedUser();

                boolean available = hasEnoughTokens(
                                user.getId(),
                                imageTokens);

                if (!available) {
                        throw new InsufficientTokensException(
                                        "Insufficient tokens");
                }
        }

        @Override
        public void consumeImageTokens(Long amount) {

                User user = getAuthenticatedUser();

                consumeTokens(
                                user.getId(),
                                amount,
                                FeatureType.IMAGE,
                                "AI Image Generation");
        }

        @Override
        public void refundImageTokens(Long amount) {

                User user = getAuthenticatedUser();

                addTokens(
                                user.getId(),
                                amount,
                                FeatureType.IMAGE,
                                "Refund Image Tokens");
        }
}