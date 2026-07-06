package com.trackai.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class WalletResponse {

    private Long totalTokens;

    private Long usedTokens;

    private Long remainingTokens;

    // NEW — frontend reads these to show "current plan"
    private String currentPlanId;

    private String currentPlanName;
}