package com.trackai.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.config.GroqModelConfig;
import com.trackai.backend.dto.chat.ChatResponse;
import com.trackai.backend.dto.groq.*;
import com.trackai.backend.exception.GroqRateLimitException;

import com.trackai.backend.service.GroqService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import reactor.core.publisher.Mono;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroqServiceImpl implements GroqService {

        private final RestTemplate restTemplate;
        private final WebClient.Builder webClientBuilder;
        private final GroqModelConfig groqModelConfig;
        private final ObjectMapper objectMapper = new ObjectMapper();

        private static final String SYSTEM_PROMPT = """
                        You are CareerForge AI, a knowledgeable and helpful assistant.

                        How to respond:
                        - Auto-detect the user's language. If the user writes Hindi/Hinglish, answer in Hindi/Hinglish. If the user writes English, answer in English.
                        - Prefer detailed, complete answers. Simple questions can be concise, but teaching, debugging, planning, and comparison questions should be thorough.
                        - Use rich Markdown formatting: short headings, bullets, numbered steps, tables, and code blocks when useful. Make the answer visually easy to scan.
                        - When helpful, briefly show visible progress sections such as "What I checked", "What I found", and "Next steps". Do not claim web search unless a web search tool was actually used.
                        - Never pad an answer with filler, generic disclaimers, or repeated points just to sound longer. Every sentence should add real information.
                        - For code questions: always provide complete, working, copy-pasteable code in proper markdown code blocks with the correct language tag.
                        - Always finish your answer completely; never stop mid-sentence or mid-list.
                        - If an image is attached, describe/analyze it carefully before answering.
                        - NEVER say you are LLaMA, GPT, DeepSeek, or any other underlying model. You are CareerForge AI.
                        """;

        private WebClient client() {
                ExchangeStrategies strategies = ExchangeStrategies.builder()
                                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                                .build();
                return webClientBuilder
                                .baseUrl(groqModelConfig.getUrl())
                                .exchangeStrategies(strategies)
                                .build();
        }

        @Override
        public List<GroqModelConfig.ModelInfo> getAvailableModels() {
                return groqModelConfig.getModels();
        }

        @Override
        public GroqModelConfig.ModelInfo getDefaultModel() {
                return groqModelConfig.resolveModel(null);
        }

        // ===================== NON-STREAMING (kept for title gen / fallback)
        // =====================
        @Override
        public ChatResponse generateResponse(List<GroqMessage> messages, String modelId) {

                GroqModelConfig.ModelInfo resolved = groqModelConfig.resolveModel(modelId);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(groqModelConfig.getApiKey());

                List<GroqMessage> messagesWithSystem = new ArrayList<>();
                messagesWithSystem.add(new GroqMessage("system", SYSTEM_PROMPT));
                messagesWithSystem.addAll(messages);

                GroqRequest groqRequest = GroqRequest.builder()
                                .model(resolved.getId())
                                .messages(messagesWithSystem)
                                .build();

                HttpEntity<GroqRequest> entity = new HttpEntity<>(groqRequest, headers);

                ResponseEntity<GroqResponse> response = restTemplate.exchange(
                                groqModelConfig.getUrl(), HttpMethod.POST, entity, GroqResponse.class);

                GroqResponse body = response.getBody();

                if (body == null || body.getChoices() == null || body.getChoices().isEmpty()) {
                        throw new RuntimeException("Failed to generate response");
                }

                return ChatResponse.builder()
                                .response(body.getChoices().get(0).getMessage().getContent())
                                .provider("GROQ")
                                .model(resolved.getId())
                                .downloadable(false)
                                .contentType("text/markdown")
                                .promptTokens(body.getUsage().getPromptTokens())
                                .completionTokens(body.getUsage().getCompletionTokens())
                                .totalTokens(body.getUsage().getTotalTokens())
                                .build();
        }

        @Override
        public String generateTitle(String prompt) {

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(groqModelConfig.getApiKey());

                List<GroqMessage> messages = List.of(
                                new GroqMessage("system",
                                                """
                                                                Generate a short title in less than 5 words.
                                                                Return only title.
                                                                """),
                                new GroqMessage("user", prompt));

                GroqRequest request = GroqRequest.builder()
                                .model(groqModelConfig.getDefaultModel())
                                .messages(messages)
                                .build();

                HttpEntity<GroqRequest> entity = new HttpEntity<>(request, headers);

                ResponseEntity<GroqResponse> response = restTemplate.exchange(
                                groqModelConfig.getUrl(), HttpMethod.POST, entity, GroqResponse.class);

                return response.getBody()
                                .getChoices()
                                .get(0)
                                .getMessage()
                                .getContent()
                                .trim();
        }

        // ===================== STREAMING =====================
        @Override
        public void streamResponse(
                        List<GroqMessage> messages,
                        String modelId,
                        String imageBase64,
                        Consumer<String> onChunk,
                        Runnable onComplete,
                        Consumer<Throwable> onError) {

                GroqModelConfig.ModelInfo resolved = groqModelConfig.resolveModel(modelId);
                boolean useImage = imageBase64 != null && !imageBase64.isBlank() && resolved.isVision();

                Object requestBody = useImage
                                ? buildVisionRequestBody(messages, resolved.getId(), imageBase64)
                                : buildTextRequestBody(messages, resolved.getId());

                client().post()
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqModelConfig.getApiKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(requestBody)
                                .retrieve()
                                // Convert Groq rate limits into a dedicated exception so the
                                // caller can immediately retry with a fallback model.
                                .onStatus(status -> status.value() == 429,
                                                clientResponse -> Mono.error(
                                                                new GroqRateLimitException(
                                                                                "Model rate-limited: "
                                                                                                + resolved.getId())))
                                .onStatus(status -> status.is5xxServerError(),
                                                clientResponse -> Mono.error(
                                                                new GroqRateLimitException(
                                                                                "Model unavailable: "
                                                                                                + resolved.getId())))
                                .bodyToFlux(String.class)
                                .doOnError(onError)
                                .subscribe(
                                                rawLine -> handleLine(rawLine, onChunk),
                                                onError,
                                                onComplete);
        }

        private GroqRequest buildTextRequestBody(List<GroqMessage> messages, String modelId) {
                List<GroqMessage> messagesWithSystem = new ArrayList<>();
                messagesWithSystem.add(new GroqMessage("system", SYSTEM_PROMPT));
                messagesWithSystem.addAll(messages);

                return GroqRequest.builder()
                                .model(modelId)
                                .messages(messagesWithSystem)
                                .stream(true)
                                .build();
        }

        private Map<String, Object> buildVisionRequestBody(List<GroqMessage> messages, String modelId,
                        String imageBase64) {
                List<Map<String, Object>> outMessages = new ArrayList<>();

                Map<String, Object> systemMsg = new LinkedHashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", SYSTEM_PROMPT);
                outMessages.add(systemMsg);

                for (int i = 0; i < messages.size(); i++) {
                        GroqMessage m = messages.get(i);
                        boolean isLast = i == messages.size() - 1;

                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("role", m.getRole());

                        if (isLast && "user".equalsIgnoreCase(m.getRole())) {
                                List<Map<String, Object>> parts = new ArrayList<>();

                                Map<String, Object> textPart = new LinkedHashMap<>();
                                textPart.put("type", "text");
                                textPart.put("text", m.getContent());
                                parts.add(textPart);

                                String url = imageBase64.startsWith("data:")
                                                ? imageBase64
                                                : "data:image/jpeg;base64," + imageBase64;

                                Map<String, Object> imagePart = new LinkedHashMap<>();
                                imagePart.put("type", "image_url");
                                Map<String, Object> imageUrl = new LinkedHashMap<>();
                                imageUrl.put("url", url);
                                imagePart.put("image_url", imageUrl);
                                parts.add(imagePart);

                                out.put("content", parts);
                        } else {
                                out.put("content", m.getContent());
                        }
                        outMessages.add(out);
                }

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", modelId);
                body.put("messages", outMessages);
                body.put("stream", true);
                body.put("max_tokens", groqModelConfig.getMaxTokens());
                return body;
        }

        private void handleLine(String rawLine, Consumer<String> onChunk) {
                if (rawLine == null)
                        return;
                String line = rawLine.trim();
                if (line.isEmpty())
                        return;

                if (line.startsWith("data:")) {
                        line = line.substring(5).trim();
                }
                if (line.isEmpty() || line.equals("[DONE]"))
                        return;

                try {
                        GroqStreamChunk chunk = objectMapper.readValue(line, GroqStreamChunk.class);
                        if (chunk.getChoices() == null || chunk.getChoices().isEmpty())
                                return;
                        GroqStreamChunk.Delta delta = chunk.getChoices().get(0).getDelta();
                        if (delta != null && delta.getContent() != null && !delta.getContent().isEmpty()) {
                                onChunk.accept(delta.getContent());
                        }
                } catch (Exception e) {
                        log.debug("Skipping unparsable Groq stream line: {}", line);
                }
        }
}
