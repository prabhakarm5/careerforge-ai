package com.trackai.backend.service;

import com.trackai.backend.dto.chat.ChatResponse;
import com.trackai.backend.dto.chat.SendMessageRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {

    ChatResponse sendMessage(SendMessageRequest request);

    /**
     * Streams the assistant's reply as Server-Sent Events.
     * Emits events of type "chunk" (raw text delta), then a final
     * "done" event carrying conversationId/title/remainingTokens as JSON,
     * or an "error" event if something goes wrong mid-stream.
     */
    SseEmitter streamMessage(SendMessageRequest request);
}