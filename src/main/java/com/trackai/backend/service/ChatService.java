package com.trackai.backend.service;

import com.trackai.backend.dto.ChatRequest;
import com.trackai.backend.dto.ChatResponse;

public interface ChatService {

    ChatResponse chat(
            ChatRequest request);
}