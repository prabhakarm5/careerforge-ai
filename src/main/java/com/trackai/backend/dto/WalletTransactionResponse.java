package com.trackai.backend.dto;

import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.enums.TransactionType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class WalletTransactionResponse {

    private Long amount;

    private TransactionType transactionType;

    private FeatureType featureType;

    private String description;

    private LocalDateTime createdAt;

    // NEW — frontend reads these to show "current plan"
    private String currentPlanId;

    private String currentPlanName;
}