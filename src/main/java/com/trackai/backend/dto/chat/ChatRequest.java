package com.trackai.backend.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequest {

    // null means new chat
    @Size(min = 5, max = 20)
    private String conversationId;

    @NotBlank
    private String message;

}