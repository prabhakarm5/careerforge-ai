package com.trackai.backend.dto.cache;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/*
 * ============================================================
 * Redis Cache DTO for Wallet
 * ============================================================
 *
 * IMPORTANT — ye normal "lazy" cache nahi hai (jaise User/Plans).
 * Ye a WRITE-THROUGH cache hai:
 *
 * - Jab bhi WalletServiceImpl DB mein wallet SAVE karta hai
 *   (addTokens/consumeTokens/applyPlanToWallet/createWallet),
 *   USI WAQT ye cache bhi FRESH data se overwrite hoti hai.
 *
 * - Isliye ye cache kabhi bhi "purana balance dikhana" wala
 *   bug nahi karegi — jab tak saara wallet-write is ek hi
 *   service (WalletServiceImpl) se ho raha hai.
 *
 * - TTL sirf ek safety-net hai, normal operation mein cache
 *   hamesha write ke saath hi refresh ho jaati hai.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CachedWallet implements Serializable {

    private String id;

    private String userId;

    private Long totalTokens;

    private Long usedTokens;

    private Long remainingTokens;

    private String currentPlanId;

    private String currentPlanName;

    private LocalDateTime updatedAt;
}