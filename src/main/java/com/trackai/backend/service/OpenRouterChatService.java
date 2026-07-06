package com.trackai.backend.service;

import com.trackai.backend.config.GroqModelConfig;
import com.trackai.backend.dto.chat.ChatResponse;
import com.trackai.backend.dto.groq.GroqMessage;

import java.util.List;
import java.util.Set;
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

    // FIX (bhai's ask: "model choose kr ke fallback"): jab current model
    // fail ho jaaye (rate-limit, credits khatam, provider down, etc.), ye
    // method decide karta hai agla kaunsa model try karna hai — current
    // conversation (messages) ki estimated size dekh kar, sirf un models
    // me se choose karta hai jinka context window use comfortably fit kar
    // sake, aur unme se sabse bada context window wala pick karta hai
    // (safest bet). Implementation OpenRouterChatServiceImpl me hai.
    // String pickFallbackModel(List<GroqMessage> messages, String excludeModelId);

    String pickFallbackModel(List<GroqMessage> messages, String currentModelId);
}