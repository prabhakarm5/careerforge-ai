package com.trackai.backend.service.impl;

import com.trackai.backend.dto.ChatRequest;
import com.trackai.backend.dto.ChatResponse;
import com.trackai.backend.dto.groq.*;

import com.trackai.backend.service.GroqService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroqServiceImpl
        implements GroqService {

    private final RestTemplate restTemplate;

    @Value("${groq.api-key}")
    private String apiKey;

    @Value("${groq.model}")
    private String model;

    @Value("${groq.url}")
    private String url;

    @Override
    public ChatResponse generateResponse(
            ChatRequest request) {

        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_JSON);

        headers.setBearerAuth(
                apiKey);

        GroqMessage message = GroqMessage.builder()
                .role("user")
                .content(request.getMessage())
                .build();

        GroqRequest groqRequest = GroqRequest.builder()
                .model(model)
                .messages(List.of(message))
                .build();

        HttpEntity<GroqRequest> entity = new HttpEntity<>(
                groqRequest,
                headers);

        ResponseEntity<GroqResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                GroqResponse.class);

        GroqResponse body = response.getBody();

        if (body == null ||
                body.getChoices() == null ||
                body.getChoices().isEmpty()) {

            throw new RuntimeException(
                    "Failed to generate response");
        }

        return ChatResponse.builder()
                .response(
                        body.getChoices()
                                .get(0)
                                .getMessage()
                                .getContent())

                .promptTokens(
                        body.getUsage()
                                .getPromptTokens())

                .completionTokens(
                        body.getUsage()
                                .getCompletionTokens())

                .totalTokens(
                        body.getUsage()
                                .getTotalTokens())

                .build();
    }
}