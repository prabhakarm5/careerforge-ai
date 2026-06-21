package com.trackai.backend.dto.chat;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageRequest {

    // null means new chat
    private String conversationId;

    @NotBlank(message = "Message is required")
    private String message;
}