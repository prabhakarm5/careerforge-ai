package com.trackai.backend.dto.chat;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ChatResponse {

    private String conversationId;

    private String title;

    private String response;

    private String provider;

    private String model;

    private Boolean downloadable;

    private String suggestedFileName;

    private String contentType;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Long remainingTokens;

}
