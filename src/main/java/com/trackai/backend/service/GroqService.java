package com.trackai.backend.service;

import com.trackai.backend.dto.ChatRequest;
import com.trackai.backend.dto.ChatResponse;

public interface GroqService {

    ChatResponse generateResponse(
            ChatRequest request);

}