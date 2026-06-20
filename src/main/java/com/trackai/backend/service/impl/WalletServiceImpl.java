package com.trackai.backend.service.impl;

import com.trackai.backend.dto.WalletResponse;
import com.trackai.backend.dto.WalletTransactionResponse;
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
        // Wallet already exists
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

    // Get current wallet
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
                .build();
    }

    // Add tokens
    @Override
    public void addTokens(
            String userId,
            Long amount,
            FeatureType featureType,
            String description) {

        Wallet wallet = walletRepository
                .findByUserId(userId)
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

    // Consume tokens
    @Override
    public void consumeTokens(
            String userId,
            Long amount,
            FeatureType featureType,
            String description) {

        Wallet wallet = walletRepository
                .findByUserId(userId)
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

    // Check token balance
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
}