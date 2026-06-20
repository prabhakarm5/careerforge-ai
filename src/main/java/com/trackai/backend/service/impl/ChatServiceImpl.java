package com.trackai.backend.service.impl;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.ChatRequest;
import com.trackai.backend.dto.ChatResponse;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.entity.ChatHistory;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.repository.ChatRepository;
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
import java.util.UUID;

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

        // Get authenticated user
        private User getAuthenticatedUser() {

                Authentication authentication = SecurityContextHolder
                                .getContext()
                                .getAuthentication();

                String email = authentication.getName();

                return userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found"));
        }

        @Override
        public ChatResponse chat(
                        ChatRequest request) {

                // Current user
                User user = getAuthenticatedUser();

                // Chat rate limit
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

                // Request blocked
                if (!rateLimitResponse.isAllowed()) {

                        throw new RuntimeException(
                                        rateLimitResponse.getMessage());
                }

                // Generate AI response
                ChatResponse response = groqService.generateResponse(
                                request);

                // Consume tokens
                // Convert AI tokens to TrackAI tokens
                long trackAiTokens = Math.max(

                                1,

                                (response.getTotalTokens() + 99) / 100);

                // Consume TrackAI tokens
                walletService.consumeTokens(

                                user.getId(),

                                trackAiTokens,

                                FeatureType.CHAT,

                                "AI Chat Request");

                // Save chat history
                ChatHistory history = ChatHistory.builder()

                                .id(UUID.randomUUID().toString())

                                .userId(user.getId())

                                .question(request.getMessage())

                                .response(response.getResponse())

                                .promptTokens(
                                                response.getPromptTokens())

                                .completionTokens(
                                                response.getCompletionTokens())

                                .totalTokens(
                                                response.getTotalTokens())

                                .createdAt(LocalDateTime.now())

                                .build();

                chatRepository.save(history);

                // Remaining wallet balance
                response.setRemainingTokens(

                                walletService
                                                .getCurrentWallet()
                                                .getRemainingTokens());
                System.out.println(
                                "Capacity = "
                                                + rateLimitProperties.getChat().getCapacity());

                System.out.println(
                                "Refill Tokens = "
                                                + rateLimitProperties.getChat().getRefillTokens());

                System.out.println(
                                "Refill Minutes = "
                                                + rateLimitProperties.getChat().getRefillMinutes());

                return response;
        }
}