package com.trackai.backend.service.impl;

import com.trackai.backend.dto.chat.ChatRequest;
import com.trackai.backend.dto.chat.ChatResponse;
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
                        List<GroqMessage> messages) {

                HttpHeaders headers = new HttpHeaders();

                headers.setContentType(
                                MediaType.APPLICATION_JSON);

                headers.setBearerAuth(
                                apiKey);

                GroqRequest groqRequest = GroqRequest.builder()
                                .model(model)
                                .messages(messages)
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

        @Override
        public String generateTitle(
                        String prompt) {

                HttpHeaders headers = new HttpHeaders();

                headers.setContentType(
                                MediaType.APPLICATION_JSON);

                headers.setBearerAuth(
                                apiKey);

                List<GroqMessage> messages = List.of(

                                new GroqMessage(

                                                "system",

                                                """
                                                                Generate a short title in less than 5 words.
                                                                Return only title.
                                                                """),

                                new GroqMessage(

                                                "user",

                                                prompt));

                GroqRequest request = GroqRequest.builder()

                                .model(
                                                model)

                                .messages(
                                                messages)

                                .build();

                HttpEntity<GroqRequest> entity = new HttpEntity<>(

                                request,

                                headers);

                ResponseEntity<GroqResponse> response =

                                restTemplate.exchange(

                                                url,

                                                HttpMethod.POST,

                                                entity,

                                                GroqResponse.class);

                return response.getBody()

                                .getChoices()

                                .get(0)

                                .getMessage()

                                .getContent()

                                .trim();
        }

}