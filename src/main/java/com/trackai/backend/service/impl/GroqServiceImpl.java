package com.trackai.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.config.GroqModelConfig;
import com.trackai.backend.dto.chat.ChatResponse;
import com.trackai.backend.dto.groq.*;
import com.trackai.backend.exception.GroqRateLimitException;
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
        // jaisa hi logic — chhota input -> chhota request, bada input ->
        // zyada room, hमेशा ABSOLUTE_MAX_TOKENS aur model ke context window
        // ke andar.
        private static final int ABSOLUTE_MAX_TOKENS = 8192;
        private static final int MIN_TOKENS = 1024;
        private static final int DEFAULT_ASSUMED_CONTEXT_WINDOW = 8192;
        private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

        // FIX (bhai's report: "answer aadha mein ruk jaata hai" + "bahut slow"):
        // Jab model apni max_tokens limit hit karta tha (finish_reason:
        // "length"), purana code ise ERROR treat karta tha aur
        // ChatServiceImpl ka fallback/continuation chain trigger hota tha —
        // jo poora accumulated text + poora memory leke agle model ko
        // bhejta tha, phir wahi truncate ho jaaye to teesre model ko... max
        // 6 baar tak. Har hop ek naya network round-trip hai (isliye slow),
        // aur agar 6 attempts ke baad bhi bade code ke liye truncate hota
        // rahe to answer bina kisi spasht "yeh incomplete hai" note ke
        // ruk jaata tha (isliye "beech mein atka hua" jaisa lagta tha).
        //
        // Naya behaviour: truncation par doosre model par kabhi jump nahi
        // karte. Bas ek chhota polite note chipka ke stream ko turant,
        // cleanly complete kar dete hain. Isse dono cheez fix — speed
        // (no more multi-model chaining) aur "atakna" (hamesha ek clean,
        // complete-feeling ending milega).
        private static final String TRUNCATION_NOTE = "\n\n---\n"
                        + "_⚠️ Yeh jawaab bahut lamba tha isliye poora ek saath nahi de paaya — "
                        + "itna bada code/answer ek single response mein possible nahi hai. "
                        + "Agla part chahiye to bas **\"continue\"** likh dijiye._";

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
                boolean useImage = imageBase64 != null && !imageBase64.isBlank() && resolved.isVision();

                int dynamicMaxTokens = computeDynamicMaxTokens(messages, resolved.getId());

                log.info("Groq stream request — model={}, dynamicMaxTokens={}", resolved.getId(), dynamicMaxTokens);

                Object requestBody = useImage
                                ? buildVisionRequestBody(messages, resolved.getId(), imageBase64, dynamicMaxTokens)
                                : buildTextRequestBody(messages, resolved.getId(), dynamicMaxTokens);

                AtomicBoolean truncated = new AtomicBoolean(false);

                client().post()
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqModelConfig.getApiKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(requestBody)
                                .retrieve()
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
                                .doOnError(err -> log.error("Groq streaming error (model={}): {}",
                                                resolved.getId(), err.toString()))
                                .subscribe(
                                                rawLine -> {
                                                        if (handleLine(rawLine, onChunk)) {
                                                                truncated.set(true);
                                                        }
                                                },
                                                onError,
                                                () -> {
                                                        // FIX: truncation ab error/fallback-chain trigger
                                                        // nahi karta. Bas ek note chipka ke turant,
                                                        // cleanly complete karte hain — fast bhi, aur
                                                        // "beech mein atka" wala feeling bhi nahi aata.
                                                        if (truncated.get()) {
                                                                log.warn(
                                                                                "Groq stream truncated at max_tokens (model={}) — finishing gracefully with note",
                                                                                resolved.getId());
                                                                onChunk.accept(TRUNCATION_NOTE);
                                                        }
                                                        onComplete.run();
                                                });
        }

        // ===================== DYNAMIC TOKEN SIZING =====================

        // FIX: Ye method OpenRouterChatServiceImpl.computeDynamicMaxTokens()
        // jaisa hi hai — input conversation ki size dekh kar decide karta
        // hai kitna max_tokens maangna hai, taaki chhota message chhota
        // request bheje aur bada message zyada room paaye, lekin hamesha
        // ABSOLUTE_MAX_TOKENS aur model ke apne context window ke andar hi
        // rahe.
        private int computeDynamicMaxTokens(List<GroqMessage> messages, String modelId) {
                int estimatedInputTokens = estimateConversationTokens(messages);

                int contextWindow = resolveContextWindow(modelId);

                int safetyBuffer = Math.max(64, (int) (contextWindow * 0.05));
                int roomForOutput = contextWindow - estimatedInputTokens - safetyBuffer;

                int proportionalCeiling = Math.max(MIN_TOKENS, estimatedInputTokens * 2);

                int candidate = Math.min(roomForOutput, proportionalCeiling);
                candidate = Math.min(candidate, ABSOLUTE_MAX_TOKENS);
                candidate = Math.max(candidate, MIN_TOKENS);

                if (candidate < MIN_TOKENS) {
                        candidate = MIN_TOKENS;
                }

                log.debug("Dynamic max_tokens calc — model={}, contextWindow={}, estimatedInput={}, "
                                + "roomForOutput={}, proportionalCeiling={}, final={}",
                                modelId, contextWindow, estimatedInputTokens, roomForOutput, proportionalCeiling,
                                candidate);

                return candidate;
        }

        // NOTE: messages list yahan already SYSTEM_PROMPT ke saath ya bina
        // dono tarah se aa sakta hai (generateResponse me pehle se system
        // add hota hai, streamResponse me nahi) — isliye system prompt ko
        // hamesha explicitly add karke estimate karte hain agar list me
        // pehle se system role wala message na ho, taaki double-count na ho.
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

                        return "length".equals(choice.getFinishReason());
                } catch (Exception e) {
                        log.debug("Skipping unparsable Groq stream line: {}", line);
                        return false;
                }
        }
}