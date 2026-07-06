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

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
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
        private final MemoryRetrievalService memoryRetrievalService;
        private final ObjectMapper objectMapper;

        private final ThreadPoolTaskExecutor streamExecutor;

        private static final Duration STREAM_HEARTBEAT = Duration.ofSeconds(15);
        private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(10);
        private static final long SSE_TIMEOUT_MS = STREAM_TIMEOUT.toMillis();

        private static final int MAX_MEMORY_MESSAGES = 20;

        private static final String EVENT_PING = "ping";

        private static final MediaType JSON = MediaType.APPLICATION_JSON;

        private final ConcurrentHashMap<String, Disposable> heartbeatTasks = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AtomicBoolean> streamClosed = new ConcurrentHashMap<>();

        public ChatServiceImpl(
                        GroqService groqService,
                        OpenRouterChatService openRouterChatService,
                        GroqModelConfig groqModelConfig,
                        WalletService walletService,
                        UserRepository userRepository,
                        RedisRateLimitService redisRateLimitService,
                        RateLimitProperties rateLimitProperties,
                        ConversationRepository conversationRepository,
                        ChatMessageRepository chatMessageRepository,
                        MemoryRetrievalService memoryRetrievalService,
                        ObjectMapper objectMapper) {

                this.groqService = groqService;
                this.openRouterChatService = openRouterChatService;
                this.groqModelConfig = groqModelConfig;
                this.walletService = walletService;
                this.userRepository = userRepository;
                this.redisRateLimitService = redisRateLimitService;
                this.rateLimitProperties = rateLimitProperties;
                this.conversationRepository = conversationRepository;
                this.chatMessageRepository = chatMessageRepository;
                this.memoryRetrievalService = memoryRetrievalService;
                this.objectMapper = objectMapper;

                this.streamExecutor = new ThreadPoolTaskExecutor();
                this.streamExecutor.setCorePoolSize(10);
                this.streamExecutor.setMaxPoolSize(50);
                this.streamExecutor.setQueueCapacity(1000);
                this.streamExecutor.setThreadNamePrefix("trackai-stream-");
                this.streamExecutor.setWaitForTasksToCompleteOnShutdown(true);
                this.streamExecutor.setAwaitTerminationSeconds(30);
                this.streamExecutor.initialize();
        }

        private void startHeartbeat(String conversationId, SseEmitter emitter) {

                AtomicBoolean closed = new AtomicBoolean(false);
                streamClosed.put(conversationId, closed);

                Disposable disposable = Flux.interval(STREAM_HEARTBEAT)
                                .subscribeOn(Schedulers.boundedElastic())
                                .takeWhile(tick -> !closed.get())
                                .subscribe(
                                                tick -> {
                                                        boolean sent = safeSend(emitter, EVENT_PING, "heartbeat");
                                                        if (!sent) {
                                                                closed.set(true);
                                                                completeEmitter(emitter, conversationId);
                                                        }
                                                },
                                                error -> {
                                                        closed.set(true);
                                                        completeEmitter(emitter, conversationId);
                                                });

                heartbeatTasks.put(conversationId, disposable);
        }

        private void registerEmitterCallbacks(String conversationId, SseEmitter emitter) {

                emitter.onCompletion(() -> {
                        log.info("SSE completed {}", conversationId);
                        completeEmitter(emitter, conversationId);
                });

                emitter.onTimeout(() -> {
                        log.warn("SSE timeout {}", conversationId);
                        completeEmitter(emitter, conversationId);
                });

                emitter.onError(ex -> {
                        log.warn("SSE error {} {}", conversationId, ex.getMessage());
                        completeEmitter(emitter, conversationId);
                });
        }

        @PreDestroy
        public void shutdown() {
                heartbeatTasks.values().forEach(Disposable::dispose);
                heartbeatTasks.clear();
                streamClosed.clear();
                streamExecutor.shutdown();
        }

        private boolean safeSend(SseEmitter emitter, String event, Object payload) {

                if (emitter == null) {
                        return false;
                }

                try {
                        SseEmitter.SseEventBuilder builder = SseEmitter.event().name(event);

                        if (payload == null) {
                                builder.data("");
                        } else if (payload instanceof String str) {
                                builder.data(str);
                        } else {
                                builder.data(objectMapper.writeValueAsString(payload), JSON);
                        }

                        emitter.send(builder);
                        return true;

                } catch (IOException ex) {
                        log.warn("Client disconnected while sending event {}", event);
                        return false;
                } catch (IllegalStateException ex) {
                        log.warn("Emitter already completed.");
                        return false;
                } catch (Exception ex) {
                        log.error("Failed to send SSE event {}", event, ex);
                        return false;
                }
        }

        private void completeEmitter(SseEmitter emitter, String conversationId) {
                try {
                        Disposable heartbeat = heartbeatTasks.remove(conversationId);
                        if (heartbeat != null) {
                                heartbeat.dispose();
                        }
                        streamClosed.remove(conversationId);
                        emitter.complete();
                } catch (Exception ignored) {
                }
        }

        private static final int MAX_MODEL_ATTEMPTS = 6;

        private static final String CONTINUATION_INSTRUCTION = "Continue your previous answer exactly from where it stopped. "
                        + "Do not repeat any earlier text, do not add any greeting, preamble, "
                        + "or note about switching models — just continue the answer seamlessly "
                        + "as if you were never interrupted.";

        // ===================== AUTH USER =====================
        private User getAuthenticatedUser() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                String email = authentication.getName();
                return userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("User not found"));
        }

        // ===================== TITLE =====================
        private String buildPlaceholderTitle(String message) {
                if (message == null || message.isBlank()) {
                        return "New Chat";
                }
                String trimmed = message.trim();
                return trimmed.length() > 60 ? trimmed.substring(0, 60) + "..." : trimmed;
        }

        // FIX (root-cause #1): this used to mutate + save the SAME
        // `conversation` Java object that the main streaming thread also
        // mutates + saves in finalizeStream() (setUpdatedAt). Two threads
        // writing the same entity instance concurrently, each calling
        // .save() independently, could stomp on each other's in-memory
        // field changes and — depending on JPA dirty-checking/flush timing —
        // throw or silently lose a write. Now this thread ONLY ever touches
        // the `title` column, via a dedicated repository method that does a
        // targeted UPDATE instead of re-saving the whole entity graph. It
        // never shares mutable state with finalizeStream() anymore.
        //
        // Add this method to ConversationRepository if missing:
        //
        // @Modifying
        // @Query("UPDATE Conversation c SET c.title = :title WHERE c.id = :id")
        // int updateTitle(@Param("id") String id, @Param("title") String title);
        //
        private void generateTitleAsync(Conversation conversation, String firstMessage, SseEmitter emitter) {
                streamExecutor.execute(() -> {
                        try {
                                String title = groqService.generateTitle(firstMessage);
                                if (title == null || title.isBlank()) {
                                        return;
                                }
                                title = title.trim();
                                if (title.length() > 80) {
                                        title = title.substring(0, 80) + "...";
                                }

                                conversationRepository.updateTitle(conversation.getId(), title);
                                // keep the in-memory copy in sync for anything reading it
                                // later in this same request lifecycle (best-effort only —
                                // never re-persisted from here).
                                conversation.setTitle(title);

                                Map<String, Object> payload = new HashMap<>();
                                payload.put("conversationId", conversation.getId());
                                payload.put("title", title);
                                safeSend(emitter, "title_updated", payload);
                        } catch (Exception e) {
                                log.warn("Async title generation failed for conversation {}: {}",
                                                conversation.getId(), e.getMessage());
                        }
                });
        }

        // ===================== CREATE CONVERSATION =====================
        private Conversation createConversation(User user, String firstMessage) {
                Conversation conversation = Conversation.builder()
                                .id(UUID.randomUUID().toString())
                                .userId(user.getId())
                                .title(buildPlaceholderTitle(firstMessage))
                                .featureType(FeatureType.CHAT)
                                .archived(false)
                                .pinned(false)
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
                                .findByConversationIdOrderByCreatedAtDesc(
                                                conversationId, PageRequest.of(0, MAX_MEMORY_MESSAGES));

                Collections.reverse(recentMessages);

                return recentMessages
                                .stream()
                                .map(message -> new GroqMessage(
                                                message.getRole().toLowerCase(),
                                                message.getContent()))
                                .collect(Collectors.toList());
        }

        // ===================== ON-DEMAND RAG =====================
        private void augmentWithRecalledContext(List<GroqMessage> memory, String conversationId,
                        String userMessage) {

                if (!memoryRetrievalService.isRecallIntent(userMessage)) {
                        return;
                }

                List<String> snippets = memoryRetrievalService.retrieveRelevant(conversationId, userMessage);
                if (snippets.isEmpty()) {
                        return;
                }

                String context = "Relevant earlier context from this conversation "
                                + "(use only if actually relevant to the current question):\n"
                                + String.join("\n---\n", snippets);

                memory.add(0, GroqMessage.builder().role("system").content(context).build());

                log.info("RAG: injected {} recalled snippet(s) for conversation {}",
                                snippets.size(), conversationId);
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

        // FIX (bhai's ask: "model choose kr ke fallback"): pehle ye method
        // sirf OpenRouter ke available models ki list se "current model
        // chhod ke pehla jo bhi mil jaaye" utha leta tha — is baat ki koi
        // guarantee nahi thi ki wo naya model current conversation ko
        // (uske context-size ke hisaab se) handle bhi kar payega ya nahi.
        // Agar conversation bada tha aur fallback model ka context window
        // chhota, wo turant ek ALAG reason (context overflow) se fail ho
        // jaata, aur fallback chain bewajah lambi ho jaati.
        //
        // Ab: poori decision OpenRouterChatServiceImpl.pickFallbackModel()
        // ko di gayi hai, jo current conversation (memory) ki estimated
        // token size dekh kar sirf un models me se choose karta hai jinka
        // context window use comfortably fit kar sake, aur unme se sabse
        // bada context window wala model pick karta hai (safest bet).
        // Isiliye ab memory bhi pass karna padta hai — sirf modelId kaafi
        // nahi tha is decision ke liye.
        //
        // REQUIRED: OpenRouterChatService interface me ye method signature
        // add karna zaroori hai, warna niche wali call compile nahi hogi:
        // String pickFallbackModel(List<GroqMessage> messages, String excludeModelId);
        private String getOpenRouterFallbackModel(String currentModelId, List<GroqMessage> memory) {
                try {
                        return openRouterChatService.pickFallbackModel(memory, currentModelId);
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
                        conversation.setTitle(generateTitleBlocking(request.getMessage()));
                        conversationRepository.save(conversation);
                } else {
                        conversation = conversationRepository.findById(request.getConversationId())
                                        .orElseThrow(() -> new RuntimeException("Conversation not found"));
                }

                saveUserMessage(conversation.getId(), request.getMessage());

                List<GroqMessage> messages = buildConversationMemory(conversation.getId());
                augmentWithRecalledContext(messages, conversation.getId(), request.getMessage());
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

        private String generateTitleBlocking(String message) {
                try {
                        String title = groqService.generateTitle(message);
                        if (title == null || title.isBlank()) {
                                return buildPlaceholderTitle(message);
                        }
                        title = title.trim();
                        return title.length() > 80 ? title.substring(0, 80) + "..." : title;
                } catch (Exception e) {
                        return buildPlaceholderTitle(message);
                }
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
                augmentWithRecalledContext(memory, conversation.getId(), request.getMessage());
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

                registerEmitterCallbacks(conversation.getId(), emitter);
                startHeartbeat(conversation.getId(), emitter);

                if (isNewConversation) {
                        generateTitleAsync(conversation, request.getMessage(), emitter);
                }

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
                                        () -> finalizeStreamSafely(emitter, user, conversation,
                                                        accumulated.toString(), "OPENROUTER", modelId),
                                        err -> {
                                                log.error("OpenRouter streaming error (attempt {}): {}",
                                                                attemptNumber, err.toString());

                                                if (attemptNumber < MAX_MODEL_ATTEMPTS) {
                                                        String fallbackModel = getOpenRouterFallbackModel(modelId,
                                                                        memory);

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

                                                if (accumulated.length() > 0) {
                                                        finalizeStreamSafely(emitter, user, conversation,
                                                                        accumulated.toString(), "OPENROUTER", modelId);
                                                        return;
                                                }
                                                sendErrorAndComplete(emitter, "OPENROUTER_ERROR",
                                                                "OpenRouter could not generate a response. Please try again or choose another model.");
                                        });
                } catch (Exception e) {
                        log.error("Unexpected OpenRouter streaming failure", e);

                        if (attemptNumber < MAX_MODEL_ATTEMPTS) {
                                String fallbackModel = getOpenRouterFallbackModel(modelId, memory);
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
                                finalizeStreamSafely(emitter, user, conversation,
                                                accumulated.toString(), "OPENROUTER", modelId);
                                return;
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
                                        chunk -> {
                                                accumulated.append(chunk);
                                                try {
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
                                        () -> finalizeStreamSafely(emitter, user, conversation,
                                                        accumulated.toString(), "GROQ", modelId),
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

                                                if (accumulated.length() > 0) {
                                                        finalizeStreamSafely(emitter, user, conversation,
                                                                        accumulated.toString(), "GROQ", modelId);
                                                        return;
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
                                finalizeStreamSafely(emitter, user, conversation,
                                                accumulated.toString(), "GROQ", modelId);
                                return;
                        }
                        sendErrorAndComplete(emitter, "GENERAL",
                                        "Something went wrong while generating the response.");
                }
        }

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

        // FIX (root-cause #1, wrapper): finalizeStream() used to be called
        // inside a try/catch that, on ANY exception (DB save failing,
        // wallet service throwing something unexpected, etc.), called
        // emitter.completeWithError(e) — which tears the HTTP connection
        // down with NO "done" and NO "error" SSE frame ever sent. The
        // frontend then sees a raw connection failure, which still preserves
        // the streamed text in its store... but ChatPage.jsx's render/commit
        // logic (fixed separately, see chatStreamStore.js /
        // ChatPage.jsx notes) required a clean "done" to ever show or commit
        // it. So a message that was 100% successfully generated could look
        // like it "disappeared".
        //
        // Now: finalizeStreamSafely() ALWAYS results in either a "done" or
        // an "error" SSE event being sent — even if the DB write for
        // conversation.updatedAt fails, we still ack the client so it can
        // commit the already-rendered text. The one thing that truly must
        // not fail silently (saving the assistant's message so it isn't
        // lost) is attempted first and separately from the "nice to have"
        // conversation.updatedAt bump.
        private void finalizeStreamSafely(SseEmitter emitter, User user, Conversation conversation,
                        String fullText, String provider, String model) {
                try {
                        finalizeStream(emitter, user, conversation, fullText, provider, model);
                } catch (Exception e) {
                        log.error("finalizeStream failed for conversation {} — sending best-effort done anyway",
                                        conversation.getId(), e);
                        // Best-effort: the assistant text was generated successfully even
                        // though bookkeeping (save/wallet) had an issue. Tell the client
                        // it's done so the UI keeps + commits what was streamed, instead
                        // of silently tearing the connection down.
                        Map<String, Object> done = new HashMap<>();
                        done.put("conversationId", conversation.getId());
                        done.put("title", conversation.getTitle());
                        done.put("totalTokens", estimateTokens(fullText));
                        safeSend(emitter, "done", done);
                        completeEmitter(emitter, conversation.getId());
                }
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
                        // Message is still saved even when tokens run out mid-generation.
                        saveAssistantMessageSafely(conversation.getId(), response);
                        bumpConversationUpdatedAtSafely(conversation);

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

                // FIX: saving the assistant's message must never be skipped —
                // this is the actual answer. It's separated from the
                // "updatedAt" bump below (which is best-effort / cosmetic)
                // so a failure bumping updatedAt can never cost us the
                // message itself.
                saveAssistantMessageSafely(conversation.getId(), response);
                bumpConversationUpdatedAtSafely(conversation);

                Wallet wallet = walletService.getWalletByUserId(user.getId());

                Map<String, Object> done = new HashMap<>();
                done.put("conversationId", conversation.getId());
                done.put("title", conversation.getTitle());
                done.put("remainingTokens", wallet.getRemainingTokens());
                done.put("totalTokens", estTokens);

                emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(done)));
                emitter.complete();
        }

        private void saveAssistantMessageSafely(String conversationId, ChatResponse response) {
                try {
                        saveAssistantMessage(conversationId, response);
                } catch (Exception e) {
                        log.error("CRITICAL: failed to save assistant message for conversation {}",
                                        conversationId, e);
                }
        }

        // FIX (root-cause #1, targeted update): no longer re-saves the whole
        // `conversation` entity (which raced with generateTitleAsync's
        // save). Uses a narrow UPDATE, and any failure here is logged but
        // never propagated — it's just the "last activity" timestamp, not
        // user-visible content.
        //
        // Add this method to ConversationRepository if missing:
        //
        // @Modifying
        // @Query("UPDATE Conversation c SET c.updatedAt = :updatedAt WHERE c.id = :id")
        // int updateTimestamp(@Param("id") String id, @Param("updatedAt") LocalDateTime
        // updatedAt);
        //
        private void bumpConversationUpdatedAtSafely(Conversation conversation) {
                try {
                        LocalDateTime now = LocalDateTime.now();
                        conversationRepository.updateTimestamp(conversation.getId(), now);
                        conversation.setUpdatedAt(now);
                } catch (Exception e) {
                        log.warn("Failed to bump updatedAt for conversation {} (non-fatal): {}",
                                        conversation.getId(), e.getMessage());
                }
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