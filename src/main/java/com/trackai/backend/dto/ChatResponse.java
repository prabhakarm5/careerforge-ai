package com.trackai.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ChatResponse {

    private String response;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Long remainingTokens;
}