package com.trackai.backend.dto.chat;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ConversationDetailsResponse {

    private String conversationId;

    private String title;

    private List<ChatMessageResponse> messages;
}