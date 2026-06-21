package com.trackai.backend.dto.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequest {

    // null means new chat
    private String conversationId;

    @NotBlank
    private String message;

}