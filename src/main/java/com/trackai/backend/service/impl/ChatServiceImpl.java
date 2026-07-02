package com.trackai.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.config.GroqModelConfig;
import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.chat.ChatResponse;
import com.trackai.backend.dto.chat.SendMessageRequest;
import com.trackai.backend.dto.groq.GroqMessage;
import com.trackai.backend.entity.ChatMessage;
import com.trackai.backend.entity.Conversation;
import com.trackai.backend.entity.User;
import com.trackai.backend.entity.Wallet;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.exception.GroqRateLimitException;
import com.trackai.backend.exception.InsufficientTokensException;
import com.trackai.backend.exception.RateLImitException;
import com.trackai.backend.repository.ChatMessageRepository;
import com.trackai.backend.repository.ConversationRepository;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.ChatService;
import com.trackai.backend.service.GroqService;
import com.trackai.backend.service.OpenRouterChatService;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

        private final GroqService groqService;
        private final OpenRouterChatService openRouterChatService;
        private final GroqModelConfig groqModelConfig;
        private final WalletService walletService;
        private final UserRepository userRepository;
        private final RedisRateLimitService redisRateLimitService;
        private final RateLimitProperties rateLimitProperties;
        private final ConversationRepository conversationRepository;
        private final ChatMessageRepository chatMessageRepository;
        private final ObjectMapper objectMapper = new ObjectMapper();

        private final Executor streamExecutor = Executors.newCachedThreadPool();

        private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

        // Ab ye sirf Groq built-in model list ke fallback attempts ki cap nahi
        // balki koi bhi provider ke liye max retry-chain length hai. Jab tak
        // groqModelConfig / openRouterChatService koi NAYA fallback model de
        // pa rahe hain, hum isse zyada attempts tak try karte rahenge (neeche
        // "fallbackModel == currentModel" check hi asli stop condition hai).
        private static final int MAX_MODEL_ATTEMPTS = 6;

        private static final String CONTINUATION_INSTRUCTION = "Continue your previous answer exactly from where it stopped. "
                        + "Do not repeat any earlier text, do not add any greeting, preamble, "
                        + "or note about switching models — just continue the answer seamlessly "
                        + "as if you were never interrupted.";

        // ===================== AUTH USER =====================
        private User getAuthenticatedUser() {
                Authentication authentication = SecurityContextHolder
                                .getContext()
                                .getAuthentication();

                String email = authentication.getName();

                return userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("User not found"));
        }

        // ===================== TITLE =====================
        private String generateTitle(String message) {
                String title;
                try {
                        title = groqService.generateTitle(message);
                } catch (Exception e) {
                        title = message;
                }
                if (title == null || title.isBlank()) {
                        title = message;
                }
                title = title.trim();
                if (title.length() > 80) {
                        title = title.substring(0, 80) + "...";
                }
                return title;
        }

        // ===================== CREATE CONVERSATION =====================
        private Conversation createConversation(User user, String firstMessage) {
                Conversation conversation = Conversation.builder()
                                .id(UUID.randomUUID().toString())
                                .userId(user.getId())
                                .title(generateTitle(firstMessage))
                                .featureType(FeatureType.CHAT)
                                .archived(false)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                return conversationRepository.save(conversation);
        }

        // ===================== SAVE USER MESSAGE =====================
        private void saveUserMessage(String conversationId, String message) {
                ChatMessage chatMessage = ChatMessage.builder()
                                .id(UUID.randomUUID().toString())
                                .conversationId(conversationId)
                                .role("USER")
                                .content(message)
                                .createdAt(LocalDateTime.now())
                                .build();
                chatMessageRepository.save(chatMessage);
        }

        // ===================== SAVE ASSISTANT =====================
        private void saveAssistantMessage(String conversationId, ChatResponse response) {
                ChatMessage chatMessage = ChatMessage.builder()
                                .id(UUID.randomUUID().toString())
                                .conversationId(conversationId)
                                .role("ASSISTANT")
                                .content(response.getResponse())
                                .promptTokens(response.getPromptTokens())
                                .completionTokens(response.getCompletionTokens())
                                .totalTokens(response.getTotalTokens())
                                .createdAt(LocalDateTime.now())
                                .build();
                chatMessageRepository.save(chatMessage);
        }

        // ===================== MEMORY =====================
        private List<GroqMessage> buildConversationMemory(String conversationId) {
                List<ChatMessage> recentMessages = chatMessageRepository
                                .findTop10ByConversationIdOrderByCreatedAtDesc(conversationId);

                Collections.reverse(recentMessages);

                return recentMessages
                                .stream()
                                .map(message -> new GroqMessage(
                                                message.getRole().toLowerCase(),
                                                message.getContent()))
                                .collect(Collectors.toList());
        }

        private int estimateTokens(String text) {
                if (text == null || text.isEmpty())
                        return 0;
                return Math.max(1, text.length() / 4);
        }

        private String resolveModelId(String requestedModel) {
                return groqModelConfig.resolveModel(requestedModel).getId();
        }

        private boolean isOpenRouterModel(String requestedModel) {
                return openRouterChatService.supportsModel(requestedModel);
        }

        private String resolveOpenRouterModelId(String requestedModel) {
                if (requestedModel != null && requestedModel.startsWith("openrouter:")) {
                        return requestedModel.substring("openrouter:".length());
                }

                return requestedModel == null || requestedModel.isBlank()
                                || "openrouter".equalsIgnoreCase(requestedModel)
                                                ? openRouterChatService.getDefaultModel()
                                                : requestedModel;
        }

        /**
         * OpenRouter ke available chat models me se, current model ke alawa
         * pehla doosra model dhoondta hai — taaki mid-stream error par
         * OpenRouter side bhi kisi aur model par switch ho sake.
         * Agar koi doosra model na mile, current model hi wapas kar deta hai
         * (jo caller ke liye "no more fallback" signal hai).
         */
        private String getOpenRouterFallbackModel(String currentModelId) {
                try {
                        return openRouterChatService.getAvailableModels()
                                        .stream()
                                        .map(GroqModelConfig.ModelInfo::getId)
                                        .filter(id -> id != null && !id.equals(currentModelId))
                                        .findFirst()
                                        .orElse(currentModelId);
                } catch (Exception e) {
                        log.warn("Could not resolve OpenRouter fallback model", e);
                        return currentModelId;
                }
        }

        // ===================== NON-STREAMING =====================
        @Override
        public ChatResponse sendMessage(SendMessageRequest request) {

                User user = getAuthenticatedUser();

                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(
                                "chat:" + user.getId(),
                                rateLimitProperties.getChat().getCapacity(),
                                rateLimitProperties.getChat().getRefillTokens(),
                                rateLimitProperties.getChat().getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {
                        throw new RateLImitException(rateLimitResponse.getMessage());
                }

                Conversation conversation;

                if (request.getConversationId() == null || request.getConversationId().isBlank()) {
                        conversation = createConversation(user, request.getMessage());
                } else {
                        conversation = conversationRepository.findById(request.getConversationId())
                                        .orElseThrow(() -> new RuntimeException("Conversation not found"));
                }

                saveUserMessage(conversation.getId(), request.getMessage());

                List<GroqMessage> messages = buildConversationMemory(conversation.getId());
                messages.add(GroqMessage.builder().role("user").content(request.getMessage()).build());

                boolean useOpenRouter = isOpenRouterModel(request.getModel());
                String modelId = useOpenRouter
                                ? resolveOpenRouterModelId(request.getModel())
                                : resolveModelId(request.getModel());
                ChatResponse response = useOpenRouter
                                ? openRouterChatService.generateResponse(messages, modelId)
                                : groqService.generateResponse(messages, modelId);

                long trackAiTokens = Math.max(1, (response.getTotalTokens() + 99) / 100);
                walletService.consumeTokens(user.getId(), trackAiTokens, FeatureType.CHAT, "AI Chat Request");

                saveAssistantMessage(conversation.getId(), response);

                conversation.setUpdatedAt(LocalDateTime.now());
                conversationRepository.save(conversation);

                response.setRemainingTokens(walletService.getWalletByUserId(user.getId()).getRemainingTokens());
                response.setConversationId(conversation.getId());
                response.setTitle(conversation.getTitle());

                return response;
        }

        // ===================== STREAMING =====================
        @Override
        public SseEmitter streamMessage(SendMessageRequest request) {

                User user = getAuthenticatedUser();

                RateLimitResponse rateLimitResponse = redisRateLimitService.allowRequest(
                                "chat:" + user.getId(),
                                rateLimitProperties.getChat().getCapacity(),
                                rateLimitProperties.getChat().getRefillTokens(),
                                rateLimitProperties.getChat().getRefillMinutes());

                SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

                if (!rateLimitResponse.isAllowed()) {
                        sendErrorAndComplete(emitter, "RATE_LIMIT", rateLimitResponse.getMessage());
                        return emitter;
                }

                Wallet wallet = walletService.getWalletByUserId(user.getId());
                if (wallet.getRemainingTokens() <= 0) {
                        sendErrorAndComplete(emitter, "NO_TOKENS",
                                        "Your tokens are exhausted. Please recharge to continue.");
                        return emitter;
                }

                final Conversation conversation;
                final boolean isNewConversation = request.getConversationId() == null
                                || request.getConversationId().isBlank();

                try {
                        if (isNewConversation) {
                                conversation = createConversation(user, request.getMessage());
                        } else {
                                conversation = conversationRepository.findById(request.getConversationId())
                                                .orElseThrow(() -> new RuntimeException("Conversation not found"));
                        }
                        saveUserMessage(conversation.getId(), request.getMessage());
                } catch (Exception e) {
                        sendErrorAndComplete(emitter, "GENERAL", "Failed to start conversation");
                        return emitter;
                }

                List<GroqMessage> memory = buildConversationMemory(conversation.getId());
                memory.add(GroqMessage.builder().role("user").content(request.getMessage()).build());

                boolean useOpenRouter = isOpenRouterModel(request.getModel());
                String requestedModelId = useOpenRouter
                                ? resolveOpenRouterModelId(request.getModel())
                                : resolveModelId(request.getModel());
                String imageBase64 = request.getImage();

                try {
                        Map<String, Object> meta = new HashMap<>();
                        meta.put("conversationId", conversation.getId());
                        meta.put("title", conversation.getTitle());
                        meta.put("isNew", isNewConversation);
                        meta.put("model", requestedModelId);
                        meta.put("provider", useOpenRouter ? "OPENROUTER" : "GROQ");
                        emitter.send(SseEmitter.event().name("meta").data(objectMapper.writeValueAsString(meta)));

                        emitter.send(SseEmitter.event().name("status")
                                        .data("Detecting language and reading conversation context"));
                        emitter.send(SseEmitter.event().name("status").data("Selected model: " + requestedModelId));
                        emitter.send(SseEmitter.event().name("status").data("Generating response"));
                } catch (Exception e) {
                        log.warn("Failed to send meta event", e);
                }

                emitter.onTimeout(() -> log.warn("SSE timed out for conversation {}", conversation.getId()));
                emitter.onError(ex -> log.warn("SSE error for conversation {}: {}", conversation.getId(),
                                ex.getMessage()));

                streamExecutor.execute(() -> {
                        if (useOpenRouter) {
                                attemptOpenRouterStream(emitter, user, conversation, memory, requestedModelId,
                                                1, new StringBuilder());
                        } else {
                                attemptStream(emitter, user, conversation, memory, requestedModelId,
                                                imageBase64, 1, new StringBuilder());
                        }
                });

                return emitter;
        }

        /**
         * modelId: is attempt me jis model ka use ho raha hai
         * memory: is attempt ko bheja jaane wala conversation context
         * (agar ye ek retry/continuation hai to isme already
         * "assistant: <ab tak ka partial jawab>" + continuation
         * instruction judi hui hoti hai)
         * accumulated: PORE stream ka ab tak ka poora text (sab attempts
         * milaakar) — yehi cheez finalizeStream() me jaati hai
         */
        private void attemptOpenRouterStream(SseEmitter emitter, User user, Conversation conversation,
                        List<GroqMessage> memory, String modelId, int attemptNumber,
                        StringBuilder accumulated) {

                try {
                        openRouterChatService.streamResponse(
                                        memory,
                                        modelId,
                                        chunk -> {
                                                accumulated.append(chunk);
                                                try {
                                                        Map<String, String> payload = new HashMap<>();
                                                        payload.put("content", chunk);
                                                        emitter.send(SseEmitter.event()
                                                                        .name("chunk")
                                                                        .data(objectMapper.writeValueAsString(payload),
                                                                                        MediaType.APPLICATION_JSON));
                                                } catch (Exception e) {
                                                        log.debug("Client disconnected mid-stream: {}", e.getMessage());
                                                }
                                        },
                                        () -> {
                                                try {
                                                        finalizeStream(emitter, user, conversation,
                                                                        accumulated.toString(), "OPENROUTER", modelId);
                                                } catch (Exception e) {
                                                        log.error("Failed to finalize OpenRouter stream", e);
                                                        try {
                                                                emitter.completeWithError(e);
                                                        } catch (Exception ignored) {
                                                        }
                                                }
                                        },
                                        err -> {
                                                log.error("OpenRouter streaming error (attempt {}): {}",
                                                                attemptNumber, err.toString());

                                                if (attemptNumber < MAX_MODEL_ATTEMPTS) {
                                                        String fallbackModel = getOpenRouterFallbackModel(modelId);

                                                        if (!fallbackModel.equals(modelId)) {
                                                                notifyModelSwitch(emitter, modelId, fallbackModel);

                                                                List<GroqMessage> continuationMemory = buildContinuationMemory(
                                                                                memory,
                                                                                accumulated);

                                                                attemptOpenRouterStream(emitter, user, conversation,
                                                                                continuationMemory, fallbackModel,
                                                                                attemptNumber + 1, accumulated);
                                                                return;
                                                        }
                                                }

                                                // Ab koi aur fallback model available nahi -- sirf yahan
                                                // partial ko final maana jaata hai, error aane ke turant
                                                // baad nahi.
                                                if (accumulated.length() > 0) {
                                                        try {
                                                                finalizeStream(emitter, user, conversation,
                                                                                accumulated.toString(), "OPENROUTER",
                                                                                modelId);
                                                                return;
                                                        } catch (Exception ignored) {
                                                        }
                                                }
                                                sendErrorAndComplete(emitter, "OPENROUTER_ERROR",
                                                                "OpenRouter could not generate a response. Please try again or choose another model.");
                                        });
                } catch (Exception e) {
                        log.error("Unexpected OpenRouter streaming failure", e);

                        if (attemptNumber < MAX_MODEL_ATTEMPTS) {
                                String fallbackModel = getOpenRouterFallbackModel(modelId);
                                if (!fallbackModel.equals(modelId)) {
                                        notifyModelSwitch(emitter, modelId, fallbackModel);
                                        List<GroqMessage> continuationMemory = buildContinuationMemory(memory,
                                                        accumulated);
                                        attemptOpenRouterStream(emitter, user, conversation,
                                                        continuationMemory, fallbackModel,
                                                        attemptNumber + 1, accumulated);
                                        return;
                                }
                        }

                        if (accumulated.length() > 0) {
                                try {
                                        finalizeStream(emitter, user, conversation,
                                                        accumulated.toString(), "OPENROUTER", modelId);
                                        return;
                                } catch (Exception ignored) {
                                }
                        }
                        sendErrorAndComplete(emitter, "OPENROUTER_ERROR",
                                        "OpenRouter could not generate a response. Please try again or choose another model.");
                }
        }

        private void attemptStream(SseEmitter emitter, User user, Conversation conversation,
                        List<GroqMessage> memory, String modelId, String imageBase64, int attemptNumber,
                        StringBuilder accumulated) {

                try {
                        groqService.streamResponse(
                                        memory,
                                        modelId,
                                        imageBase64,
                                        // onChunk
                                        chunk -> {
                                                accumulated.append(chunk);
                                                try {
                                                        // Send chunks as JSON so whitespace is preserved across
                                                        // WebClient, SseEmitter, proxies, and frontend parsing.
                                                        Map<String, String> payload = new HashMap<>();
                                                        payload.put("content", chunk);
                                                        emitter.send(SseEmitter.event()
                                                                        .name("chunk")
                                                                        .data(objectMapper
                                                                                        .writeValueAsString(payload),
                                                                                        MediaType.APPLICATION_JSON));
                                                } catch (Exception e) {
                                                        log.debug("Client disconnected mid-stream: {}", e.getMessage());
                                                }
                                        },
                                        // onComplete
                                        () -> {
                                                try {
                                                        finalizeStream(emitter, user, conversation,
                                                                        accumulated.toString(), "GROQ", modelId);
                                                } catch (Exception e) {
                                                        log.error("Failed to finalize stream", e);
                                                        try {
                                                                emitter.completeWithError(e);
                                                        } catch (Exception ignored) {
                                                        }
                                                }
                                        },
                                        // onError -- ab sirf GroqRateLimitException nahi, HAR stream
                                        // error par (network drop, socket reset, provider 5xx, etc.)
                                        // fallback model try karega, jab tak attempts bache hain.
                                        err -> {
                                                log.error("Groq streaming error (attempt {}): {}",
                                                                attemptNumber, err.toString());

                                                if (attemptNumber < MAX_MODEL_ATTEMPTS) {
                                                        String fallbackModel = groqModelConfig
                                                                        .getFallbackModel(modelId).getId();

                                                        if (!fallbackModel.equals(modelId)) {
                                                                notifyModelSwitch(emitter, modelId, fallbackModel);

                                                                List<GroqMessage> continuationMemory = buildContinuationMemory(
                                                                                memory,
                                                                                accumulated);

                                                                attemptStream(emitter, user, conversation,
                                                                                continuationMemory, fallbackModel,
                                                                                imageBase64, attemptNumber + 1,
                                                                                accumulated);
                                                                return;
                                                        }

                                                        log.error("No alternate model available to fall back to");
                                                }

                                                // Ab koi aur fallback nahi bacha -- ab hi (aur sirf ab)
                                                // partial response ko final answer maana jaayega.
                                                if (accumulated.length() > 0) {
                                                        try {
                                                                finalizeStream(emitter, user, conversation,
                                                                                accumulated.toString(), "GROQ",
                                                                                modelId);
                                                                return;
                                                        } catch (Exception ignored) {
                                                        }
                                                }
                                                sendErrorAndComplete(emitter, "GROQ_BUSY",
                                                                "All AI models are busy right now. Please try again shortly.");
                                        });
                } catch (Exception e) {
                        log.error("Unexpected streaming failure (attempt {})", attemptNumber, e);

                        if (attemptNumber < MAX_MODEL_ATTEMPTS) {
                                String fallbackModel = groqModelConfig.getFallbackModel(modelId).getId();
                                if (!fallbackModel.equals(modelId)) {
                                        notifyModelSwitch(emitter, modelId, fallbackModel);
                                        List<GroqMessage> continuationMemory = buildContinuationMemory(memory,
                                                        accumulated);
                                        attemptStream(emitter, user, conversation, continuationMemory,
                                                        fallbackModel, imageBase64, attemptNumber + 1, accumulated);
                                        return;
                                }
                        }

                        if (accumulated.length() > 0) {
                                try {
                                        finalizeStream(emitter, user, conversation,
                                                        accumulated.toString(), "GROQ", modelId);
                                        return;
                                } catch (Exception ignored) {
                                }
                        }
                        sendErrorAndComplete(emitter, "GENERAL",
                                        "Something went wrong while generating the response.");
                }
        }

        /**
         * Model switch hone par frontend ko inform karta hai. Best-effort hai
         * -- fail ho jaaye to bhi stream continue hota hai, is wajah se
         * blocking nahi hoga.
         */
        private void notifyModelSwitch(SseEmitter emitter, String fromModel, String toModel) {
                try {
                        Map<String, Object> switched = new HashMap<>();
                        switched.put("from", fromModel);
                        switched.put("to", toModel);
                        switched.put("message",
                                        "\"" + fromModel + "\" ne response beech me rok diya. "
                                                        + "Switched to \"" + toModel
                                                        + "\" automatically to continue the same answer.");
                        emitter.send(SseEmitter.event()
                                        .name("model_switched")
                                        .data(objectMapper.writeValueAsString(switched)));
                } catch (Exception ignored) {
                }
        }

        /**
         * Jab model switch hota hai, purana original memory copy karke usme
         * ab tak ka partial assistant answer + ek "continue from here"
         * instruction add karta hai, taaki naya model shuru se jawab na de
         * balki wahi se aage badhaye jaha purana model ruka tha.
         */
        private List<GroqMessage> buildContinuationMemory(List<GroqMessage> originalMemory,
                        StringBuilder accumulated) {
                List<GroqMessage> continuationMemory = new ArrayList<>(originalMemory);

                if (accumulated.length() > 0) {
                        continuationMemory.add(GroqMessage.builder()
                                        .role("assistant")
                                        .content(accumulated.toString())
                                        .build());
                        continuationMemory.add(GroqMessage.builder()
                                        .role("user")
                                        .content(CONTINUATION_INSTRUCTION)
                                        .build());
                }

                return continuationMemory;
        }

        private void finalizeStream(SseEmitter emitter, User user, Conversation conversation, String fullText)
                        throws Exception {

                finalizeStream(emitter, user, conversation, fullText, null, null);
        }

        private void finalizeStream(SseEmitter emitter, User user, Conversation conversation, String fullText,
                        String provider, String model)
                        throws Exception {

                int estTokens = estimateTokens(fullText);

                ChatResponse response = ChatResponse.builder()
                                .response(fullText)
                                .provider(provider)
                                .model(model)
                                .downloadable(fullText != null && fullText.contains("```"))
                                .contentType("text/markdown")
                                .promptTokens(0)
                                .completionTokens(estTokens)
                                .totalTokens(estTokens)
                                .build();

                long trackAiTokens = Math.max(1, (estTokens + 99) / 100);

                try {
                        walletService.consumeTokens(user.getId(), trackAiTokens, FeatureType.CHAT, "AI Chat Request");
                } catch (InsufficientTokensException e) {
                        saveAssistantMessage(conversation.getId(), response);
                        conversation.setUpdatedAt(LocalDateTime.now());
                        conversationRepository.save(conversation);

                        Map<String, Object> done = new HashMap<>();
                        done.put("conversationId", conversation.getId());
                        done.put("title", conversation.getTitle());
                        done.put("remainingTokens", 0);
                        done.put("totalTokens", estTokens);
                        done.put("tokensExhausted", true);
                        emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(done)));

                        Map<String, String> err = new HashMap<>();
                        err.put("code", "NO_TOKENS");
                        err.put("message", "Your tokens are exhausted. Please recharge to continue.");
                        emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(err)));
                        emitter.complete();
                        return;
                }

                saveAssistantMessage(conversation.getId(), response);
                conversation.setUpdatedAt(LocalDateTime.now());
                conversationRepository.save(conversation);

                Wallet wallet = walletService.getWalletByUserId(user.getId());

                Map<String, Object> done = new HashMap<>();
                done.put("conversationId", conversation.getId());
                done.put("title", conversation.getTitle());
                done.put("remainingTokens", wallet.getRemainingTokens());
                done.put("totalTokens", estTokens);

                emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(done)));
                emitter.complete();
        }

        private void sendErrorAndComplete(SseEmitter emitter, String code, String message) {
                try {
                        Map<String, String> err = new HashMap<>();
                        err.put("code", code);
                        err.put("message", message);
                        emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(err)));
                } catch (Exception ignored) {
                } finally {
                        try {
                                emitter.complete();
                        } catch (Exception ignored) {
                        }
                }
        }
}