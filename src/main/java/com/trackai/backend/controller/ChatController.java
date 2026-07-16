package com.trackai.backend.controller;

import com.trackai.backend.config.GroqModelConfig;
import com.trackai.backend.dto.WalletResponse;
import com.trackai.backend.dto.chat.ChatModelResponse;
import com.trackai.backend.dto.chat.ChatResponse;
import com.trackai.backend.dto.chat.SendMessageRequest;
import com.trackai.backend.service.ChatService;
import com.trackai.backend.service.GroqService;
import com.trackai.backend.service.OpenRouterChatService;
import com.trackai.backend.service.WalletService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final GroqService groqService;
    private final OpenRouterChatService openRouterChatService;
    private final WalletService walletService;

    /**
     * Non-streaming endpoint — kept as-is for backward compatibility
     * (e.g. any internal tooling, retries, or older clients).
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody SendMessageRequest request) {

        return ResponseEntity.ok(chatService.sendMessage(request));
    }

    /**
     * Streaming endpoint — returns Server-Sent Events.
     *
     * Event sequence the frontend should expect:
     * 1. "meta" — { conversationId, title, isNew, model } (fired immediately)
     * 2. "chunk" — raw text delta (fired repeatedly)
     * 3. "done" — { conversationId, title, remainingTokens, totalTokens }
     * OR
     * "error" — { code, message } (fired once, ends the stream)
     *
     * NOTE: This must be called with a POST body, so on the frontend you
     * cannot use the native EventSource API (which only supports GET).
     * Use fetch() with a ReadableStream reader instead (see frontend code).
     */
    @PostMapping(path = "/stream", produces = "text/event-stream")
    public SseEmitter streamChat(
            @Valid @RequestBody SendMessageRequest request) {

        return chatService.streamMessage(request);
    }

    /**
     * Lists the models the frontend can let the user pick from — driven
     * entirely by the "groq.models" list in application.yml. Each entry
     * carries a "vision" flag so the UI can disable image-attach for
     * models that can't read images.
     */
    @GetMapping("/models")
    public ResponseEntity<List<ChatModelResponse>> getModels() {
        WalletResponse wallet = walletService.getCurrentWallet();
        List<GroqModelConfig.ModelInfo> models = new ArrayList<>(groqService.getAvailableModels());
        models.addAll(openRouterChatService.getAvailableModels());

        return ResponseEntity.ok(models.stream().map(model -> toResponse(model, wallet)).toList());
    }

    private ChatModelResponse toResponse(GroqModelConfig.ModelInfo model, WalletResponse wallet) {
        long minimum = Math.max(1, model.getMinimumCredits());
        boolean recharged = wallet.getCurrentPlanId() != null && !wallet.getCurrentPlanId().isBlank();
        boolean locked = model.isPremium()
                && (!recharged || wallet.getRemainingTokens() < minimum);
        String reason = !locked ? null
                : !recharged ? "Recharge once to unlock this model"
                : "Add credits to use this model";
        return new ChatModelResponse(
                model.getId(), model.getLabel(), model.getDescription(), model.isVision(),
                model.getType(), model.isPremium(), minimum, locked, reason);
    }
}
