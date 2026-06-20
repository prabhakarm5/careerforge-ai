package com.trackai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequest {

    @NotBlank(message = "Message is required")
    private String message;
}