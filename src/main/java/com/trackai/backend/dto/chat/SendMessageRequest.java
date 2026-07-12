package com.trackai.backend.dto.chat;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageRequest {

    // null means new chat
    private String conversationId;

    // @NotBlank(message = "Message is required")
    private String message;

    // Selected Groq model id from the frontend dropdown (see groq.models in
    // application.yml). Optional — backend falls back to groq.default-model
    // if this is null/blank/unknown, so old clients still work fine.
    private String model;

    // Optional base64 / data-URL image, only honoured when the resolved
    // model has vision: true in application.yml. Ignored otherwise.
    private String image;

    // Optional user preference: concise (default) or balanced.
    private String responseStyle;
}