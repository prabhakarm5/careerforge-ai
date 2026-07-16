package com.trackai.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.config.GroqModelConfig;
import com.trackai.backend.dto.chat.ChatResponse;
import com.trackai.backend.dto.groq.*;
import com.trackai.backend.exception.GroqRateLimitException;
import com.trackai.backend.service.ChatResponsePolicy;
import com.trackai.backend.service.GroqService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroqServiceImpl implements GroqService {

        private final RestTemplate restTemplate;
        private final WebClient.Builder webClientBuilder;
        private final GroqModelConfig groqModelConfig;
        private final ObjectMapper objectMapper;
        private static final int MAX_IN_MEMORY = 16 * 1024 * 1024;

        private static final int CONNECT_TIMEOUT = 30; // seconds
        private static final int RESPONSE_TIMEOUT = 120; // seconds

        private volatile WebClient webClient;

        // FIX (dynamic max_tokens): pehle groqModelConfig.getMaxTokens() ek
        // fixed value tha, har request (chhota "hi" ho ya bada essay) same
        // max_tokens maangti thi. Ab input conversation ki size dekh kar
        // dynamically decide karte hain, exactly OpenRouterChatServiceImpl
        // jaisa hi logic Ã¢â‚¬â€ chhota input -> chhota request, bada input ->
        // zyada room, hÃ Â¤Â®Ã Â¥â€¡Ã Â¤Â¶Ã Â¤Â¾ ABSOLUTE_MAX_TOKENS aur model ke context window
        // ke andar.
        private static final int ABSOLUTE_MAX_TOKENS = 8192;
        private static final int MIN_TOKENS = 16;
        private static final int DEFAULT_ASSUMED_CONTEXT_WINDOW = 8192;
        private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

        // A provider can cap one response even when the requested artifact is valid.
        // Continue only after finish_reason=length, using the same model and stream.
        private static final int MAX_AUTO_CONTINUATIONS = 5;
        private static final String CONTINUATION_PROMPT = "Continue exactly from the next character after your previous output. "
                        + "Do not repeat, summarize, restart, add a preamble, reopen an existing code fence, or mention continuation. "
                        + "Complete the full requested code or document, including every remaining closing fence and tag.";

        private static final String SYSTEM_PROMPT = """
                        You are CareerForge AI, a knowledgeable and helpful assistant.

                        Response rules:
                        - The latest user message is the active instruction. Its requested language, output format,
                          and length override earlier tasks. Never continue an earlier artifact unless asked now.
                        - Match the latest user's language. Adapt answer length to the actual request and the supplied
                          response-style instruction. Ordinary questions can be concise, while code, pages, files,
                          documents, and detailed explanations must include every necessary section.
                        - Use headings, lists, tables, and code blocks only when they help the current request.
                        - For an ambiguous short follow-up, ask one short context-aware question. Do not guess a new
                          task, repeat an old answer, or dump code.
                        - If the user asks for an exact reply such as "only say yes", output exactly that and nothing else.
                        - For explicit code/file/page requests, provide a complete working result. Never output mojibake,
                          giant data URLs, invented web-search claims, answer-budget notices, or continuation instructions.
                        - The client already provides artifact preview, copy, and download controls. Never output CodePen,
                          StackBlitz, or JSFiddle links, raw HTML anchor tags, Blob URLs, data:text/html URLs, or a separate
                          preview-links section.
                        - For a web page or single-file HTML request, put the entire source in exactly one Markdown code
                          fence labelled html. Include doctype, head, body, and closing html tag. Never emit raw HTML
                          outside that fence and never leave a half-written property, section, code fence, or open tag.
                        - If an image is attached, analyze it before answering.
                        - Never identify yourself as an underlying model. You are CareerForge AI.
                        """;

        private WebClient client() {

                if (webClient != null) {
                        return webClient;
                }

                synchronized (this) {

                        if (webClient != null) {
                                return webClient;
                        }

                        ExchangeStrategies strategies = ExchangeStrategies.builder()
                                        .codecs(configurer -> configurer.defaultCodecs()
                                                        .maxInMemorySize(MAX_IN_MEMORY))
                                        .build();

                        HttpClient httpClient = HttpClient.create()
                                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT * 1000)
                                        .responseTimeout(java.time.Duration.ofSeconds(RESPONSE_TIMEOUT))
                                        .doOnConnected(conn -> conn
                                                        .addHandlerLast(new ReadTimeoutHandler(RESPONSE_TIMEOUT,
                                                                        TimeUnit.SECONDS))
                                                        .addHandlerLast(new WriteTimeoutHandler(CONNECT_TIMEOUT,
                                                                        TimeUnit.SECONDS)));

                        webClient = webClientBuilder
                                        .baseUrl(groqModelConfig.getUrl())
                                        .clientConnector(new ReactorClientHttpConnector(httpClient))
                                        .exchangeStrategies(strategies)
                                        .defaultHeader(HttpHeaders.AUTHORIZATION,
                                                        "Bearer " + groqModelConfig.getApiKey())
                                        .defaultHeader(HttpHeaders.CONTENT_TYPE,
                                                        MediaType.APPLICATION_JSON_VALUE)
                                        .build();

                        return webClient;
                }

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

                int dynamicMaxTokens = computeDynamicMaxTokens(messagesWithSystem, resolved.getId());

                GroqRequest groqRequest = GroqRequest.builder()
                                .model(resolved.getId())
                                .messages(messagesWithSystem)
                                .maxTokens(dynamicMaxTokens)
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

        // ===================== TITLE GENERATION =====================
        @Override
        public String generateTitle(String prompt) {

                GroqModelConfig.ModelInfo model = groqModelConfig.resolveModel(null);

                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(groqModelConfig.getApiKey());
                headers.setContentType(MediaType.APPLICATION_JSON);

                List<GroqMessage> messages = List.of(
                                new GroqMessage(
                                                "system",
                                                """
                                                                Generate a concise conversation title.

                                                                Rules:
                                                                - Maximum 5 words
                                                                - No quotes
                                                                - No punctuation at start/end
                                                                - Return title only
                                                                """),
                                new GroqMessage("user", prompt));

                GroqRequest request = GroqRequest.builder()
                                .model(model.getId())
                                .messages(messages)
                                .maxTokens(30)
                                .build();

                HttpEntity<GroqRequest> entity = new HttpEntity<>(request, headers);

                try {

                        ResponseEntity<GroqResponse> response = restTemplate.exchange(
                                        groqModelConfig.getUrl(),
                                        HttpMethod.POST,
                                        entity,
                                        GroqResponse.class);

                        GroqResponse body = response.getBody();

                        if (body == null
                                        || body.getChoices() == null
                                        || body.getChoices().isEmpty()) {

                                return "New Chat";

                        }

                        return body.getChoices()
                                        .get(0)
                                        .getMessage()
                                        .getContent()
                                        .trim();

                } catch (Exception ex) {

                        log.warn("Title generation failed : {}", ex.getMessage());

                        return "New Chat";

                }

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
                streamSegment(messages, resolved, imageBase64, 0, new StringBuilder(), onChunk, onComplete, onError);
        }

        private void streamSegment(
                        List<GroqMessage> baseMessages,
                        GroqModelConfig.ModelInfo resolved,
                        String imageBase64,
                        int continuationIndex,
                        StringBuilder accumulated,
                        Consumer<String> onChunk,
                        Runnable onComplete,
                        Consumer<Throwable> onError) {

                List<GroqMessage> requestMessages = continuationIndex == 0
                                ? baseMessages
                                : buildContinuationMessages(baseMessages, accumulated.toString());
                boolean useImage = continuationIndex == 0
                                && imageBase64 != null
                                && !imageBase64.isBlank()
                                && resolved.isVision();
                int dynamicMaxTokens = computeDynamicMaxTokens(requestMessages, resolved.getId());

                log.info("Groq stream request - model={}, dynamicMaxTokens={}, continuation={}",
                                resolved.getId(), dynamicMaxTokens, continuationIndex);

                Object requestBody = useImage
                                ? buildVisionRequestBody(requestMessages, resolved.getId(), imageBase64, dynamicMaxTokens)
                                : buildTextRequestBody(requestMessages, resolved.getId(), dynamicMaxTokens);
                AtomicBoolean truncated = new AtomicBoolean(false);

                client().post()
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqModelConfig.getApiKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(requestBody)
                                .retrieve()
                                .onStatus(status -> status.value() == 429,
                                                clientResponse -> Mono.error(new GroqRateLimitException(
                                                                "Model rate-limited: " + resolved.getId())))
                                .onStatus(status -> status.is5xxServerError(),
                                                clientResponse -> Mono.error(new GroqRateLimitException(
                                                                "Model unavailable: " + resolved.getId())))
                                .bodyToFlux(String.class)
                                .doOnError(err -> log.error("Groq streaming error (model={}): {}",
                                                resolved.getId(), err.toString()))
                                .subscribe(
                                                rawLine -> {
                                                        if (handleLine(rawLine, chunk -> {
                                                                accumulated.append(chunk);
                                                                onChunk.accept(chunk);
                                                        })) {
                                                                truncated.set(true);
                                                        }
                                                },
                                                onError,
                                                () -> {
                                                        if (truncated.get() && continuationIndex < MAX_AUTO_CONTINUATIONS) {
                                                                log.info("Groq output limit reached; continuing same stream automatically (part {})",
                                                                                continuationIndex + 2);
                                                                streamSegment(baseMessages, resolved, null,
                                                                                continuationIndex + 1, accumulated,
                                                                                onChunk, onComplete, onError);
                                                                return;
                                                        }
                                                        if (truncated.get()) {
                                                                log.warn("Groq output remained truncated after {} automatic continuations (model={})",
                                                                                MAX_AUTO_CONTINUATIONS, resolved.getId());
                                                        }
                                                        onComplete.run();
                                                });
        }

        private List<GroqMessage> buildContinuationMessages(List<GroqMessage> baseMessages, String accumulated) {
                List<GroqMessage> continuation = new ArrayList<>(baseMessages);
                continuation.add(new GroqMessage("assistant", accumulated));
                continuation.add(new GroqMessage("user", CONTINUATION_PROMPT));
                return continuation;
        }

        // ===================== DYNAMIC TOKEN SIZING =====================

        // FIX: Ye method OpenRouterChatServiceImpl.computeDynamicMaxTokens()
        // jaisa hi hai Ã¢â‚¬â€ input conversation ki size dekh kar decide karta
        // hai kitna max_tokens maangna hai, taaki chhota message chhota
        // request bheje aur bada message zyada room paaye, lekin hamesha
        // ABSOLUTE_MAX_TOKENS aur model ke apne context window ke andar hi
        // rahe.
        private int computeDynamicMaxTokens(List<GroqMessage> messages, String modelId) {
                int estimatedInputTokens = estimateConversationTokens(messages);
                int contextWindow = resolveContextWindow(modelId);
                int safetyBuffer = Math.max(64, (int) (contextWindow * 0.05));
                int roomForOutput = Math.max(MIN_TOKENS, contextWindow - estimatedInputTokens - safetyBuffer);
                int requested = ChatResponsePolicy.recommendedMaxOutputTokens(messages, ABSOLUTE_MAX_TOKENS);
                int candidate = Math.max(MIN_TOKENS, Math.min(requested, roomForOutput));

                log.debug("Dynamic max_tokens calc - model={}, contextWindow={}, estimatedInput={}, "
                                + "roomForOutput={}, requested={}, final={}",
                                modelId, contextWindow, estimatedInputTokens, roomForOutput, requested, candidate);
                return candidate;
        }
        private int estimateConversationTokens(List<GroqMessage> messages) {
                boolean hasSystemMessage = messages != null && messages.stream()
                                .anyMatch(m -> "system".equalsIgnoreCase(m.getRole()));

                int total = hasSystemMessage ? 0 : estimateTokens(SYSTEM_PROMPT);

                if (messages != null) {
                        for (GroqMessage m : messages) {
                                total += estimateTokens(m.getContent());
                                total += 4; // role/formatting overhead per message
                        }
                }
                return total;
        }

        private int resolveContextWindow(String modelId) {
                try {
                        for (GroqModelConfig.ModelInfo info : groqModelConfig.getModels()) {
                                if (info.getId() != null && info.getId().equals(modelId)) {
                                        int configured = info.getContextLength();
                                        if (configured > 0) {
                                                return configured;
                                        }
                                }
                        }
                } catch (Exception e) {
                        log.debug("Could not resolve configured context window for Groq model {}, using default",
                                        modelId);
                }
                return DEFAULT_ASSUMED_CONTEXT_WINDOW;
        }

        private int estimateTokens(String text) {
                if (text == null || text.isEmpty()) {
                        return 0;
                }
                return Math.max(1, (int) Math.ceil(text.length() / (double) CHARS_PER_TOKEN_ESTIMATE));
        }

        private GroqRequest buildTextRequestBody(
                        List<GroqMessage> messages,
                        String modelId,
                        int maxTokens) {

                List<GroqMessage> requestMessages = new ArrayList<>();

                requestMessages.add(
                                new GroqMessage(
                                                "system",
                                                SYSTEM_PROMPT));

                requestMessages.addAll(messages);

                return GroqRequest.builder()
                                .model(modelId)
                                .messages(requestMessages)
                                .stream(true)
                                .maxTokens(maxTokens)
                                .build();

        }

        private Map<String, Object> buildVisionRequestBody(List<GroqMessage> messages, String modelId,
                        String imageBase64, int maxTokens) {
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
                body.put("max_tokens", maxTokens);
                return body;
        }

        private boolean handleLine(String rawLine, Consumer<String> onChunk) {
                if (rawLine == null)
                        return false;
                String line = rawLine.trim();
                if (line.isEmpty())
                        return false;

                if (line.startsWith("data:")) {
                        line = line.substring(5).trim();
                }
                if (line.isEmpty() || line.equals("[DONE]"))
                        return false;

                try {
                        GroqStreamChunk chunk = objectMapper.readValue(line, GroqStreamChunk.class);
                        if (chunk.getChoices() == null || chunk.getChoices().isEmpty())
                                return false;

                        GroqStreamChunk.Choice choice = chunk.getChoices().get(0);
                        GroqStreamChunk.Delta delta = choice.getDelta();
                        if (delta != null && delta.getContent() != null && !delta.getContent().isEmpty()) {
                                onChunk.accept(delta.getContent());
                        }

                        String finishReason = choice.getFinishReason();
                        return finishReason != null && (
                                        "length".equalsIgnoreCase(finishReason)
                                        || "max_tokens".equalsIgnoreCase(finishReason)
                                        || "max_output_tokens".equalsIgnoreCase(finishReason)
                                        || "token_limit".equalsIgnoreCase(finishReason));
                } catch (Exception e) {
                        log.debug("Skipping unparsable Groq stream line: {}", line);
                        return false;
                }
        }
}