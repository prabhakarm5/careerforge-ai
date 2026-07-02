package com.trackai.backend.service;

import com.trackai.backend.config.GroqModelConfig;
import com.trackai.backend.dto.chat.ChatResponse;
import com.trackai.backend.dto.groq.GroqMessage;

import java.util.List;
import java.util.function.Consumer;

public interface OpenRouterChatService {

    ChatResponse generateResponse(List<GroqMessage> messages, String modelId);

    void streamResponse(
            List<GroqMessage> messages,
            String modelId,
            Consumer<String> onChunk,
            Runnable onComplete,
            Consumer<Throwable> onError);

    String getDefaultModel();

    boolean supportsModel(String modelId);

    List<GroqModelConfig.ModelInfo> getAvailableModels();
}
