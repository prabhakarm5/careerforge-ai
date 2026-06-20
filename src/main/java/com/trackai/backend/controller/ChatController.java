package com.trackai.backend.controller;

import com.trackai.backend.dto.ChatRequest;
import com.trackai.backend.dto.ChatResponse;
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

    @PostMapping
    public ResponseEntity<ChatResponse> chat(

            @Valid @RequestBody ChatRequest request) {

        return ResponseEntity.ok(

                chatService.chat(request));
    }
}