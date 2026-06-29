package com.trackai.backend.controller;

import com.trackai.backend.dto.chat.ChatRequest;
import com.trackai.backend.dto.chat.ChatResponse;
import com.trackai.backend.dto.chat.SendMessageRequest;
import com.trackai.backend.service.ChatService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Send a chat message.
     *
     * Request body example for a NEW conversation:
     * {
     * "message": "Hello, how are you?"
     * }
     *
     * Request body example to CONTINUE an existing conversation
     * (this is how "click on a chat and keep chatting in it" works):
     * {
     * "message": "What did I ask you earlier?",
     * "conversationId": "the-id-you-got-from-GET-/api/conversations"
     * }
     *
     * If conversationId is null/blank -> a brand new conversation is created.
     * If conversationId is provided -> the message is appended to that
     * same conversation (handled inside ChatServiceImpl.sendMessage()).
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody SendMessageRequest request) {

        return ResponseEntity.ok(

                chatService.sendMessage(
                        request));
    }

}