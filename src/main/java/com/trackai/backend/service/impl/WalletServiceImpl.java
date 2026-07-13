package com.trackai.backend.service.impl;

import com.trackai.backend.dto.WalletResponse;
import com.trackai.backend.security.JwtUserPrincipal;
import com.trackai.backend.dto.WalletTransactionResponse;
import com.trackai.backend.dto.cache.CachedUser;
import com.trackai.backend.dto.cache.CachedWallet;
import com.trackai.backend.entity.SubscriptionPlan;
import com.trackai.backend.entity.User;
import com.trackai.backend.entity.Wallet;
import com.trackai.backend.entity.WalletTransaction;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.enums.Role;
import com.trackai.backend.enums.TransactionType;
import com.trackai.backend.exception.InsufficientTokensException;
import com.trackai.backend.exception.WalletAlreadyExistsException;
import com.trackai.backend.exception.WalletNotFoundException;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.repository.WalletRepository;
import com.trackai.backend.repository.WalletTransactionRepository;
import com.trackai.backend.service.RedisUserCacheService;
import com.trackai.backend.service.RedisWalletCacheService;
import com.trackai.backend.service.WalletService;

import jakarta.transaction.Transactional;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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

        // FIX: existing pattern reuse — user lookup bhi ab cache-first
        private final RedisUserCacheService redisUserCacheService;

        // FIX: naya — wallet ka WRITE-THROUGH cache
        private final RedisWalletCacheService redisWalletCacheService;

        @Value("${app.wallet.initial-tokens}")
        private Long initialTokens;

        @Value("${trackai.tokens.image}")
        private Long imageTokens;

        // ==========================================================
        // Get authenticated user
        //
        // FIX: ye method bhi (jaise ChatServiceImpl mein tha) seedha
        // DB hit karta tha har call pe. hasEnoughTokens/consumeImageTokens
        // jaisi methods isko call karti hain, jo frequently trigger hoti
        // hain. Ab REDIS-FIRST pattern use kar rahe hain — same jo
        // UserServiceImpl/ChatServiceImpl mein hai. Poore codebase mein
        // ab ye ek CONSISTENT, single source-of-truth pattern hai.
        // ==========================================================
        private User getAuthenticatedUser() {

                Authentication authentication = SecurityContextHolder
                                .getContext()
                                .getAuthentication();

                String email = authentication.getName();

                // Fast path for current access tokens: identity is already signed into JWT.
                if (authentication.getPrincipal() instanceof JwtUserPrincipal principal
                                && principal.userId() != null && !principal.userId().isBlank()) {
                        return User.builder()
                                        .id(principal.userId())
                                        .email(principal.email())
                                        .role(com.trackai.backend.enums.Role.valueOf(principal.role()))
                                        .build();
                }

                // STEP-1: REDIS
                CachedUser cachedUser = redisUserCacheService.getUser(email);

                if (cachedUser != null) {

                        return User.builder()
                                        .id(cachedUser.getId())
                                        .name(cachedUser.getName())
                                        .email(cachedUser.getEmail())
                                        .role(cachedUser.getRole())
                                        .enabled(cachedUser.getEnabled())
                                        .blocked(cachedUser.getBlocked())
                                        .emailVerified(cachedUser.getEmailVerified())
                                        .mobileNumber(cachedUser.getMobileNumber())
                                        .profileImage(cachedUser.getProfileImage())
                                        .profileImagePublicId(cachedUser.getProfileImagePublicId())
                                        .description(cachedUser.getDescription())
                                        .createdAt(cachedUser.getCreatedAt())
                                        .build();
                }

                // STEP-2: DATABASE
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "Authenticated user not found"));

                // STEP-3: SAVE CACHE
                if (user.getRole() != Role.ROLE_ADMIN) {

                        CachedUser toCache = CachedUser.builder()
                                        .id(user.getId())
                                        .name(user.getName())
                                        .email(user.getEmail())
                                        .role(user.getRole())
                                        .enabled(user.getEnabled())
                                        .blocked(user.getBlocked())
                                        .emailVerified(user.getEmailVerified())
                                        .mobileNumber(user.getMobileNumber())
                                        .profileImage(user.getProfileImage())
                                        .profileImagePublicId(user.getProfileImagePublicId())
                                        .description(user.getDescription())
                                        .createdAt(user.getCreatedAt())
                                        .build();

                        redisUserCacheService.saveUser(toCache);
                }

                return user;
        }

        // ==========================================================
        // Helper: Wallet entity -> CachedWallet, aur turant Redis mein
        // save karo. Har jagah jahan wallet DB mein save hota hai,
        // iske turant baad ye helper call hoga (WRITE-THROUGH).
        // ==========================================================
        private void syncWalletCache(Wallet wallet) {

                CachedWallet cachedWallet = CachedWallet.builder()
                                .id(wallet.getId())
                                .userId(wallet.getUserId())
                                .totalTokens(wallet.getTotalTokens())
                                .usedTokens(wallet.getUsedTokens())
                                .remainingTokens(wallet.getRemainingTokens())
                                .currentPlanId(wallet.getCurrentPlanId())
                                .currentPlanName(wallet.getCurrentPlanName())
                                .updatedAt(wallet.getUpdatedAt())
                                .build();

                redisWalletCacheService.saveWallet(cachedWallet);
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

                // FIX: naya wallet bana hi hai to turant cache mein bhi daal do —
                // agla hi request (jaise turant chat try karna) cache-hit paayega
                syncWalletCache(wallet);

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


        private Wallet createMissingWalletAndReturn(String userId) {
                try {
                        createWallet(userId);
                } catch (WalletAlreadyExistsException ignored) {
                        // Concurrent dashboard/sidebar calls can both notice a missing cache.
                        // If another request creates the wallet first, just read it below.
                }

                return walletRepository
                                .findByUserId(userId)
                                .orElseThrow(() -> new WalletNotFoundException(
                                                "Wallet not found after auto-create"));
        }
        // Get current wallet (via SecurityContext)
        //
        // FIX: ab REDIS-FIRST. Ye dashboard/wallet-page load pe call
        // hoti hai — bahut frequent read. Write-through cache ki wajah
        // se ye hamesha up-to-date rahegi.
        @Override
        public WalletResponse getCurrentWallet() {

                User user = getAuthenticatedUser();

                // STEP-1: REDIS
                CachedWallet cached = redisWalletCacheService.getWallet(user.getId());

                if (cached != null) {

                        return WalletResponse.builder()
                                        .totalTokens(cached.getTotalTokens())
                                        .usedTokens(cached.getUsedTokens())
                                        .remainingTokens(cached.getRemainingTokens())
                                        .currentPlanId(cached.getCurrentPlanId())
                                        .currentPlanName(cached.getCurrentPlanName())
                                        .build();
                }

                // STEP-2: DATABASE (cache MISS)
                Wallet wallet = walletRepository
                                .findByUserId(user.getId())
                                .orElseGet(() -> createMissingWalletAndReturn(user.getId()));

                // STEP-3: SAVE CACHE
                syncWalletCache(wallet);

                return WalletResponse.builder()
                                .totalTokens(wallet.getTotalTokens())
                                .usedTokens(wallet.getUsedTokens())
                                .remainingTokens(wallet.getRemainingTokens())
                                .currentPlanId(wallet.getCurrentPlanId())
                                .currentPlanName(wallet.getCurrentPlanName())
                                .build();
        }

        // Get wallet by explicit userId — SAFE to call from ANY thread
        //
        // FIX: ye method ChatServiceImpl ke streaming path mein baar-baar
        // call hoti hai (har message finalize hone par). Ab cache-first —
        // isse chat ke hottest path se DB load significantly kam hoga.
        //
        // NOTE: return type Wallet hai (poora entity), lekin cache-hit
        // case mein hum ek "detached" Wallet object bana rahe hain jo
        // sirf data-carrier ki tarah use hoga (jaise ChatServiceImpl
        // isse sirf .getRemainingTokens() padhne ke liye use karta hai) —
        // ise dobara save() mat karna, warna DB out-of-sync ho sakta hai.
        @Override
        public Wallet getWalletByUserId(String userId) {

                // STEP-1: REDIS
                CachedWallet cached = redisWalletCacheService.getWallet(userId);

                if (cached != null) {

                        return Wallet.builder()
                                        .id(cached.getId())
                                        .userId(cached.getUserId())
                                        .totalTokens(cached.getTotalTokens())
                                        .usedTokens(cached.getUsedTokens())
                                        .remainingTokens(cached.getRemainingTokens())
                                        .currentPlanId(cached.getCurrentPlanId())
                                        .currentPlanName(cached.getCurrentPlanName())
                                        .updatedAt(cached.getUpdatedAt())
                                        .build();
                }

                // STEP-2: DATABASE (cache MISS)
                Wallet wallet = walletRepository
                                .findByUserId(userId)
                                .orElseGet(() -> createMissingWalletAndReturn(userId));

                // STEP-3: SAVE CACHE
                syncWalletCache(wallet);

                return wallet;
        }

        // Add tokens — row-level DB lock (unchanged locking logic)
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

                // FIX: WRITE-THROUGH — DB update hote hi cache bhi turant
                // fresh data se overwrite. Isse balance kabhi stale nahi
                // dikhega, chahe payment webhook se aaya ho ya refund se.
                syncWalletCache(wallet);

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

        // Apply subscription plan to wallet
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

                // FIX: WRITE-THROUGH — plan activate hote hi naya
                // currentPlanId/currentPlanName bhi cache mein reflect ho
                syncWalletCache(wallet);

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

        // Consume tokens — same locked-read, prevents negative balance race
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

                // FIX: WRITE-THROUGH — ye SABSE ZYADA HIT hone wala write
                // hai (har chat message pe call hoti hai). Isliye cache ko
                // yahan turant refresh karna sabse zyada important hai —
                // isi ke bad next getWalletByUserId() call (finalizeStream
                // ke andar, usi request mein) already-fresh cache paayegi.
                syncWalletCache(wallet);

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
        //
        // FIX: cache-first — checkImageGenerationTokens() se call hoti
        // hai, jo har image generation attempt pe chalti hai
        @Override
        public boolean hasEnoughTokens(
                        String userId,
                        Long amount) {

                CachedWallet cached = redisWalletCacheService.getWallet(userId);

                if (cached != null) {
                        return cached.getRemainingTokens() >= amount;
                }

                Wallet wallet = walletRepository
                                .findByUserId(userId)
                                .orElseGet(() -> createMissingWalletAndReturn(userId));

                syncWalletCache(wallet);

                return wallet.getRemainingTokens() >= amount;
        }

        // Transaction history — FIX NAHI KIYA (jaanbhoojkar)
        //
        // NOTE: Ye list cache NAHI ki. Transaction history ek
        // append-only audit log hai jo continuously badhta rehta hai,
        // pagination ho sakti hai future mein, aur "thoda purana dikh
        // jaana" acceptable nahi hai financial records ke liye.
        // Cache karne ka fayda bhi kam hai kyunki ye page kam frequently
        // khulta hai (dashboard jaisa har-message-load nahi hai).
        @Override
        public List<WalletTransactionResponse> getTransactionHistory() {

                User user = getAuthenticatedUser();

                return walletTransactionRepository
                                .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 100))
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