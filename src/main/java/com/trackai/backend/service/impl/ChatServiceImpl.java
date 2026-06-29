package com.trackai.backend.service.impl;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.chat.ChatRequest;
import com.trackai.backend.dto.chat.ChatResponse;
import com.trackai.backend.dto.chat.SendMessageRequest;
import com.trackai.backend.dto.groq.GroqMessage;
import com.trackai.backend.entity.ChatHistory;
import com.trackai.backend.entity.ChatMessage;
import com.trackai.backend.entity.Conversation;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.exception.RateLImitException;
import com.trackai.backend.repository.ChatMessageRepository;
import com.trackai.backend.repository.ChatRepository;
import com.trackai.backend.repository.ConversationRepository;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.ChatService;
import com.trackai.backend.service.GroqService;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.WalletService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl
                implements ChatService {

        private final GroqService groqService;

        private final WalletService walletService;

        private final ChatRepository chatRepository;

        private final UserRepository userRepository;

        private final RedisRateLimitService redisRateLimitService;

        private final RateLimitProperties rateLimitProperties;

        private final ConversationRepository conversationRepository;

        private final ChatMessageRepository chatMessageRepository;

        // ===================== AUTH USER =====================
        private User getAuthenticatedUser() {

                Authentication authentication = SecurityContextHolder
                                .getContext()
                                .getAuthentication();

                String email = authentication.getName();

                return userRepository
                                .findByEmail(email)

                                .orElseThrow(() ->

                                new RuntimeException(
                                                "User not found"));
        }

        // ===================== TITLE =====================
        private String generateTitle(String message) {
                String title;

                try {
                        title = groqService.generateTitle(message);
                } catch (Exception e) {
                        title = message;
                }

                // null/blank guard
                if (title == null || title.isBlank()) {
                        title = message;
                }

                // hard cap — Groq ka title bhi ho to truncate
                title = title.trim();
                if (title.length() > 80) {
                        title = title.substring(0, 80) + "...";
                }

                return title;
        }

        // ===================== CREATE CONVERSATION =====================
        private Conversation createConversation(
                        User user,
                        String firstMessage) {

                Conversation conversation = Conversation.builder()

                                .id(
                                                UUID.randomUUID()
                                                                .toString())

                                .userId(
                                                user.getId())

                                .title(
                                                generateTitle(
                                                                firstMessage))

                                .featureType(
                                                FeatureType.CHAT)

                                .archived(
                                                false)

                                .createdAt(
                                                LocalDateTime.now())

                                .updatedAt(
                                                LocalDateTime.now())

                                .build();

                return conversationRepository.save(
                                conversation);
        }

        // ===================== SAVE USER MESSAGE =====================
        private void saveUserMessage(String conversationId, String message) {

                ChatMessage chatMessage = ChatMessage.builder()

                                .id(
                                                UUID.randomUUID()
                                                                .toString())

                                .conversationId(
                                                conversationId)

                                .role(
                                                "USER")

                                .content(
                                                message)

                                .createdAt(
                                                LocalDateTime.now())

                                .build();

                chatMessageRepository.save(
                                chatMessage);
        }

        // ===================== SAVE ASSISTANT =====================
        private void saveAssistantMessage(
                        String conversationId,
                        ChatResponse response) {

                ChatMessage chatMessage = ChatMessage.builder()

                                .id(
                                                UUID.randomUUID()
                                                                .toString())

                                .conversationId(
                                                conversationId)

                                .role(
                                                "ASSISTANT")

                                .content(
                                                response.getResponse())

                                .promptTokens(
                                                response.getPromptTokens())

                                .completionTokens(
                                                response.getCompletionTokens())

                                .totalTokens(
                                                response.getTotalTokens())

                                .createdAt(
                                                LocalDateTime.now())

                                .build();

                chatMessageRepository.save(
                                chatMessage);
        }

        // ===================== MEMORY =====================
        // NOTE: Stream.toList() returns an IMMUTABLE list, which caused
        // UnsupportedOperationException when we later called messages.add(...)
        // in sendMessage(). Switched to Collectors.toList() which returns a
        // mutable ArrayList, so .add() works fine now.
        // Top20 → Top10 karo (memory kam, TPM limit hit nahi hogi)
        private List<GroqMessage> buildConversationMemory(String conversationId) {
                return chatMessageRepository
                                .findTop10ByConversationIdOrderByCreatedAtAsc(conversationId)
                                .stream()
                                .map(message -> new GroqMessage(
                                                message.getRole().toLowerCase(),
                                                message.getContent()))
                                .collect(Collectors.toList());
        }

        @Override
        public ChatResponse sendMessage(
                        SendMessageRequest request) {

                User user = getAuthenticatedUser();

                // RATE LIMIT
                RateLimitResponse rateLimitResponse =

                                redisRateLimitService.allowRequest(

                                                "chat:" + user.getId(),

                                                rateLimitProperties
                                                                .getChat()
                                                                .getCapacity(),

                                                rateLimitProperties
                                                                .getChat()
                                                                .getRefillTokens(),

                                                rateLimitProperties
                                                                .getChat()
                                                                .getRefillMinutes());

                if (!rateLimitResponse.isAllowed()) {

                        throw new RateLImitException(rateLimitResponse.getMessage());
                }

                Conversation conversation;

                // ================= NEW CHAT =================
                if (request.getConversationId() == null ||
                                request.getConversationId().isBlank()) {

                        conversation = createConversation(

                                        user,

                                        request.getMessage());

                }

                // ================= OLD CHAT =================
                else {

                        conversation = conversationRepository

                                        .findById(

                                                        request.getConversationId())

                                        .orElseThrow(() ->

                                        new RuntimeException(

                                                        "Conversation not found"));
                }

                // SAVE USER MESSAGE
                saveUserMessage(

                                conversation.getId(),

                                request.getMessage());

                // LOAD MEMORY
                List<GroqMessage> messages =

                                buildConversationMemory(

                                                conversation.getId());

                // CURRENT USER PROMPT
                messages.add(

                                GroqMessage.builder()

                                                .role("user")

                                                .content(

                                                                request.getMessage())

                                                .build());

                // AI RESPONSE
                ChatResponse response =

                                groqService.generateResponse(

                                                messages);

                // CONSUME TOKENS
                long trackAiTokens = Math.max(

                                1,

                                (response.getTotalTokens() + 99) / 100);

                walletService.consumeTokens(

                                user.getId(),

                                trackAiTokens,

                                FeatureType.CHAT,

                                "AI Chat Request");

                // SAVE ASSISTANT
                saveAssistantMessage(

                                conversation.getId(),

                                response);

                // UPDATE CONVERSATION
                conversation.setUpdatedAt(

                                LocalDateTime.now());

                conversationRepository.save(

                                conversation);

                // REMAINING TOKENS
                response.setRemainingTokens(

                                walletService

                                                .getCurrentWallet()

                                                .getRemainingTokens());

                response.setConversationId(

                                conversation.getId());

                response.setTitle(

                                conversation.getTitle());

                return response;
        }

}