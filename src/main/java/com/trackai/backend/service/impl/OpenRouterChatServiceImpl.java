package com.trackai.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.config.GroqModelConfig;
import com.trackai.backend.config.OpenRouterProperties;
import com.trackai.backend.dto.chat.ChatResponse;
import com.trackai.backend.dto.groq.GroqMessage;
import com.trackai.backend.dto.groq.GroqResponse;
import com.trackai.backend.dto.groq.GroqStreamChunk;
import com.trackai.backend.exception.OpenRouterException;
import com.trackai.backend.service.OpenRouterChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.SocketException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenRouterChatServiceImpl implements OpenRouterChatService {

    private final WebClient openRouterWebClient;
    private final OpenRouterProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are CareerForge AI, a knowledgeable and helpful assistant.
            Auto-detect the user's language. If the user writes Hindi/Hinglish, answer in Hindi/Hinglish. If the user writes English, answer in English.
            Prefer detailed, complete answers with rich Markdown formatting: short headings, bullets, numbered steps, tables, and code blocks when useful.
            When helpful, briefly show visible progress sections such as "What I checked", "What I found", and "Next steps".
            If the user asks for an app, page, or downloadable code, provide clean Markdown with separate fenced code blocks and short filenames. Do not output giant data: URLs, raw HTML anchor download links, or mojibake text.
            Do not claim web search unless a web search tool was actually used.
            Never say you are a specific underlying model. You are CareerForge AI.
            """;

    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(1);

    // Dynamic max_tokens sizing: chhota input -> chhota request, bada input ->
    // zyada room, hamesha ABSOLUTE_MAX_TOKENS aur model ke context window ke
    // andar.
    private static final int ABSOLUTE_MAX_TOKENS = 8192;
    private static final int MIN_TOKENS = 1024;

    // Conservative default jab model ka apna context-window config me na mile.
    private static final int DEFAULT_ASSUMED_CONTEXT_WINDOW = 16000;

    // Chars-to-tokens rough-but-safe estimate.
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    // FIX (bhai's ask: "agar answer token se zyada ka hai to short mein
    // answer de"): agar computed budget (dynamicMaxTokens) already tight
    // hai — matlab lambi conversation history ya lamba input hone ki wajah
    // se model ke paas likhne ke liye zyada room nahi bacha — hum model ko
    // ek EXTRA system instruction ke through PROACTIVELY bata dete hain ki
    // chhota, seedha jawaab do, bajaye is ke ki wo apna "normal detailed"
    // jawaab likhna shuru kare aur beech mein max_tokens hit karke truncate
    // ho jaaye. Threshold ABSOLUTE_MAX_TOKENS ka ek-chauthai (2048) rakha
    // hai — isse kam room bacha ho to "tight" maana jaata hai.
    private static final int CONCISE_MODE_THRESHOLD_TOKENS = ABSOLUTE_MAX_TOKENS / 4;

    private static final String CONCISE_MODE_INSTRUCTION_TEMPLATE = "IMPORTANT (overrides the detailed-answer "
            + "preference above): the response budget for this reply is limited to about %d tokens right now. "
            + "Give a noticeably SHORTER, more concise answer than you normally would — cover only the most "
            + "important points, skip extra examples or repetition, and do not pad the answer. If real detail "
            + "must be cut, say so briefly at the end and invite the user to ask a follow-up (or type "
            + "\"continue\") for more.";

    // Truncation par model-hop nahi karte — ek chhota polite note chipka ke
    // turant, cleanly stream complete kar dete hain. (Ab CONCISE_MODE se
    // zyada rare hoga, kyunki model ko pehle hi bata diya jaata hai budget
    // tight hai — ye sirf tab trigger hoga jab model instruction ke bawajood
    // bhi limit se zyada likhne ki koshish kare.)
    private static final String TRUNCATION_NOTE = "\n\n---\n"
            + "_⚠️ Yeh jawaab bahut lamba tha isliye poora ek saath nahi de paaya — "
            + "itna bada code/answer ek single response mein possible nahi hai. "
            + "Agla part chahiye to bas **\"continue\"** likh dijiye._";

    @Override
    public ChatResponse generateResponse(List<GroqMessage> messages, String modelId) {

        String resolvedModelId = resolveModel(modelId);
        int dynamicMaxTokens = computeDynamicMaxTokens(messages, resolvedModelId);

        GroqResponse body = openRouterWebClient
                .post()
                .uri(properties.getChatEndpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequestBody(messages, resolvedModelId, false, dynamicMaxTokens))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapErrorResponse)
                .bodyToMono(GroqResponse.class)
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_BACKOFF)
                        .filter(this::isTransientNetworkError)
                        .doBeforeRetry(sig -> log.warn(
                                "Retrying OpenRouter request after transient network error (attempt {}): {}",
                                sig.totalRetries() + 1, sig.failure().toString())))
                .block();

        if (body == null || body.getChoices() == null || body.getChoices().isEmpty()) {
            throw new OpenRouterException("OpenRouter chat response is empty.");
        }

        Integer promptTokens = body.getUsage() == null ? 0 : body.getUsage().getPromptTokens();
        Integer completionTokens = body.getUsage() == null ? 0 : body.getUsage().getCompletionTokens();
        Integer totalTokens = body.getUsage() == null
                ? estimateTokens(body.getChoices().get(0).getMessage().getContent())
                : body.getUsage().getTotalTokens();

        return ChatResponse.builder()
                .response(body.getChoices().get(0).getMessage().getContent())
                .provider("OPENROUTER")
                .model(resolvedModelId)
                .downloadable(looksLikeCodeOrDocument(body.getChoices().get(0).getMessage().getContent()))
                .suggestedFileName(suggestFileName(body.getChoices().get(0).getMessage().getContent()))
                .contentType("text/markdown")
                .promptTokens(promptTokens == null ? 0 : promptTokens)
                .completionTokens(completionTokens == null ? 0 : completionTokens)
                .totalTokens(totalTokens == null ? 0 : totalTokens)
                .build();
    }

    @Override
    public void streamResponse(
            List<GroqMessage> messages,
            String modelId,
            Consumer<String> onChunk,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        AtomicBoolean streamStarted = new AtomicBoolean(false);
        AtomicBoolean truncated = new AtomicBoolean(false);

        String resolvedModelId = resolveModel(modelId);
        int dynamicMaxTokens = computeDynamicMaxTokens(messages, resolvedModelId);

        log.info("OpenRouter stream request — model={}, dynamicMaxTokens={}", resolvedModelId, dynamicMaxTokens);

        openRouterWebClient
                .post()
                .uri(properties.getChatEndpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequestBody(messages, resolvedModelId, true, dynamicMaxTokens))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapErrorResponse)
                .bodyToFlux(String.class)
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_BACKOFF)
                        .filter(ex -> isTransientNetworkError(ex) && !streamStarted.get())
                        .doBeforeRetry(sig -> log.warn(
                                "Retrying OpenRouter stream after transient network error (attempt {}): {}",
                                sig.totalRetries() + 1, sig.failure().toString())))
                .doOnError(err -> {
                    if (isTransientNetworkError(err)) {
                        log.error("OpenRouter streaming error (network) after retries exhausted", err);
                    } else {
                        log.error("OpenRouter streaming error: {}", err.getMessage());
                    }
                })
                .subscribe(
                        rawLine -> {
                            streamStarted.set(true);
                            if (handleLine(rawLine, onChunk)) {
                                truncated.set(true);
                            }
                        },
                        onError,
                        () -> {
                            if (truncated.get()) {
                                log.warn(
                                        "OpenRouter stream truncated at max_tokens (model={}) — finishing gracefully with note",
                                        modelId);
                                onChunk.accept(TRUNCATION_NOTE);
                            }
                            onComplete.run();
                        });
    }

    @Override
    public String getDefaultModel() {
        return properties.getChatModel();
    }

    @Override
    public boolean supportsModel(String modelId) {
        return properties.supportsChatModel(modelId);
    }

    @Override
    public List<GroqModelConfig.ModelInfo> getAvailableModels() {
        List<GroqModelConfig.ModelInfo> models = new ArrayList<>();

        for (OpenRouterProperties.ModelInfo item : properties.getChatModels()) {
            GroqModelConfig.ModelInfo model = new GroqModelConfig.ModelInfo();
            model.setId(item.getId());
            model.setLabel(item.getLabel());
            model.setDescription(item.getDescription());
            model.setVision(item.isVision());
            model.setProvider("OPENROUTER");
            model.setType("chat");
            model.setContextLength(item.getContextLength());
            models.add(model);
        }

        return models;
    }

    // ===================== SMART FALLBACK SELECTION =====================

    // FIX (bhai's ask: "model choose kr ke fallback"): pehle
    // ChatServiceImpl.getOpenRouterFallbackModel() bas "list me se pehla
    // model jo current wala nahi hai" utha leta tha — bina ye check kiye ki
    // wo model current conversation ko context-wise handle bhi kar payega
    // ya nahi. Isse aisa ho sakta tha ki hum ek chhote-context wale model
    // par hop karein jabki conversation already bada hai — wo model turant
    // context-overflow se fail ho jaata, aur fallback chain bewajah lambi
    // ho jaati.
    //
    // Ab: current conversation ka estimated size dekh kar, sirf un models
    // me se choose karte hain jinka context window is conversation ko
    // (+ ek MIN_TOKENS jitna likhne ka room) comfortably fit kar sake. Agar
    // multiple qualify karein, sabse bada context-window wala lete hain
    // (safest bet). Agar koi bhi qualify na kare (conversation itni badi hai
    // ki koi bhi configured model usse fit nahi kar sakta), tab bhi best-
    // effort ke roop me sabse bade context-window wale model par jaate hain
    // — bilkul refuse karne se behtar hai try karna.
    //
    // NOTE: Isko use karne ke liye OpenRouterChatService interface me ye
    // method signature add karna zaroori hai:
    // String pickFallbackModel(List<GroqMessage> messages, String excludeModelId);
    @Override
    public String pickFallbackModel(List<GroqMessage> messages, String excludeModelId) {
        List<OpenRouterProperties.ModelInfo> candidates = properties.getChatModels();
        if (candidates == null || candidates.isEmpty()) {
            return excludeModelId;
        }

        int estimatedInputTokens = estimateConversationTokens(messages);
        int roomNeeded = estimatedInputTokens + MIN_TOKENS;

        OpenRouterProperties.ModelInfo bestFit = null;
        OpenRouterProperties.ModelInfo largestOverall = null;

        for (OpenRouterProperties.ModelInfo candidate : candidates) {
            if (candidate.getId() == null || candidate.getId().equals(excludeModelId)) {
                continue;
            }

            if (largestOverall == null || candidate.getContextLength() > largestOverall.getContextLength()) {
                largestOverall = candidate;
            }

            if (candidate.getContextLength() >= roomNeeded
                    && (bestFit == null || candidate.getContextLength() > bestFit.getContextLength())) {
                bestFit = candidate;
            }
        }

        if (bestFit != null) {
            log.info("Fallback model chosen (context-fit): {} (contextLength={}, needed>={})",
                    bestFit.getId(), bestFit.getContextLength(), roomNeeded);
            return bestFit.getId();
        }
        if (largestOverall != null) {
            log.warn("No fallback model comfortably fits this conversation (needed>={} tokens); "
                    + "using largest available anyway: {} (contextLength={})",
                    roomNeeded, largestOverall.getId(), largestOverall.getContextLength());
            return largestOverall.getId();
        }
        return excludeModelId;
    }

    // ===================== DYNAMIC TOKEN SIZING =====================

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
                modelId, contextWindow, estimatedInputTokens, roomForOutput, proportionalCeiling, candidate);

        return candidate;
    }

    private int estimateConversationTokens(List<GroqMessage> messages) {
        int total = estimateTokens(SYSTEM_PROMPT);
        if (messages != null) {
            for (GroqMessage m : messages) {
                total += estimateTokens(m.getContent());
                total += 4;
            }
        }
        return total;
    }

    // FIX (restored): pichli file me ye method ke andar sirf ek TODO reh
    // gaya tha (contextLength field abhi tak OpenRouterProperties.ModelInfo
    // me exist hi nahi karta tha, isliye .getContextLength() call hata diya
    // gaya tha taaki compile ho sake). Ab OpenRouterProperties.ModelInfo me
    // contextLength field maujood hai, isliye asli logic wapas laga diya —
    // configured value use hoga jab bhi available ho, warna safe default.
    private int resolveContextWindow(String modelId) {
        try {
            for (OpenRouterProperties.ModelInfo item : properties.getChatModels()) {
                if (item.getId() != null && item.getId().equals(modelId)) {
                    int configured = item.getContextLength();
                    if (configured > 0) {
                        return configured;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve configured context window for model {}, using default", modelId);
        }
        return DEFAULT_ASSUMED_CONTEXT_WINDOW;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / (double) CHARS_PER_TOKEN_ESTIMATE));
    }

    // ===================== REQUEST BUILDING =====================

    private Map<String, Object> buildRequestBody(List<GroqMessage> messages, String modelId, boolean stream,
            int maxTokens) {
        List<Map<String, String>> outMessages = new ArrayList<>();
        outMessages.add(message("system", SYSTEM_PROMPT));

        // FIX (bhai's ask: "agar answer token se zyada ka hai to short mein
        // answer de"): budget tight hone par model ko proactively concise
        // rehne ka instruction dete hain, taaki wo khud hi apna jawaab
        // budget ke hisaab se chhota rakhe — bajaye is ke ki wo normal-size
        // jawaab likhna shuru kare aur max_tokens hit karke beech me kat
        // jaaye.
        if (maxTokens < CONCISE_MODE_THRESHOLD_TOKENS) {
            outMessages.add(message("system", String.format(CONCISE_MODE_INSTRUCTION_TEMPLATE, maxTokens)));
            log.info("Concise-mode instruction injected — budget only {} tokens (model={})", maxTokens, modelId);
        }

        for (GroqMessage item : messages) {
            outMessages.add(message(item.getRole(), item.getContent()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);
        body.put("messages", outMessages);
        body.put("stream", stream);
        body.put("max_tokens", maxTokens);
        return body;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String resolveModel(String modelId) {
        return properties.resolveChatModel(modelId);
    }

    private Mono<Throwable> mapErrorResponse(ClientResponse response) {
        int statusCode = response.statusCode().value();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(rawBody -> {
                    log.warn("OpenRouter returned HTTP {} — raw body: {}", statusCode, rawBody);
                    return new OpenRouterException(buildErrorMessage(statusCode, rawBody));
                });
    }

    private String buildErrorMessage(int statusCode, String rawBody) {
        String providerMessage = extractProviderMessage(rawBody);

        if (statusCode == 402
                || containsAny(providerMessage, "insufficient", "credit", "balance", "payment required")) {
            return "OPENROUTER_INSUFFICIENT_CREDITS: OpenRouter account/model par credits khatam ya insufficient hain. "
                    + "OpenRouter dashboard me balance check karein. Detail: " + providerMessage;
        }

        if (statusCode == 429 || containsAny(providerMessage, "rate limit", "too many requests")) {
            return "OPENROUTER_RATE_LIMIT: OpenRouter/model abhi rate-limited hai (free models par common hai). "
                    + "Thodi der baad phir try karein. Detail: " + providerMessage;
        }

        if (statusCode == 401 || statusCode == 403) {
            return "OPENROUTER_AUTH_ERROR: OpenRouter API key invalid ya unauthorized hai. Detail: " + providerMessage;
        }

        if (statusCode == 503 || statusCode == 502) {
            return "OPENROUTER_PROVIDER_UNAVAILABLE: Is model ka upstream provider abhi unavailable hai. "
                    + "Doosra model try karein ya thodi der baad retry karein. Detail: " + providerMessage;
        }

        return "OPENROUTER_ERROR (HTTP " + statusCode + "): "
                + (providerMessage.isEmpty() ? rawBody : providerMessage);
    }

    private String extractProviderMessage(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode error = root.path("error");
            if (error.isMissingNode()) {
                return rawBody;
            }

            StringBuilder sb = new StringBuilder();

            JsonNode msg = error.path("message");
            if (!msg.isMissingNode() && !msg.asText().isBlank()) {
                sb.append(msg.asText());
            }

            JsonNode metadata = error.path("metadata");
            if (!metadata.isMissingNode()) {
                JsonNode providerName = metadata.path("provider_name");
                if (!providerName.isMissingNode() && !providerName.asText().isBlank()) {
                    sb.append(" [provider: ").append(providerName.asText()).append("]");
                }
                JsonNode raw = metadata.path("raw");
                if (!raw.isMissingNode() && !raw.asText().isBlank()) {
                    sb.append(" — underlying: ").append(raw.asText());
                } else if (!raw.isMissingNode() && !raw.isTextual()) {
                    sb.append(" — underlying: ").append(raw.toString());
                }
            }

            return sb.length() > 0 ? sb.toString() : rawBody;
        } catch (Exception e) {
            log.debug("Could not parse OpenRouter error body as JSON: {}", rawBody);
        }
        return rawBody;
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null)
            return false;
        String lower = text.toLowerCase();
        for (String needle : needles) {
            if (lower.contains(needle))
                return true;
        }
        return false;
    }

    private boolean isTransientNetworkError(Throwable ex) {
        if (ex == null)
            return false;
        if (ex instanceof OpenRouterException)
            return false;
        if (ex instanceof SocketException)
            return true;
        if (ex instanceof WebClientRequestException)
            return true;

        Throwable cause = ex.getCause();
        int depth = 0;
        while (cause != null && depth < 5) {
            if (cause instanceof SocketException || cause instanceof WebClientRequestException) {
                return true;
            }
            cause = cause.getCause();
            depth++;
        }

        String msg = ex.getMessage();
        return msg != null && (msg.contains("Connection reset")
                || msg.contains("Connection prematurely closed")
                || msg.contains("connection error"));
    }

    private boolean handleLine(String rawLine, Consumer<String> onChunk) {
        if (rawLine == null) {
            return false;
        }

        String line = rawLine.trim();
        if (line.isEmpty()) {
            return false;
        }

        if (line.startsWith("data:")) {
            line = line.substring(5).trim();
        }

        if (line.isEmpty() || "[DONE]".equals(line)) {
            return false;
        }

        try {
            GroqStreamChunk chunk = objectMapper.readValue(line, GroqStreamChunk.class);
            if (chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
                return false;
            }

            GroqStreamChunk.Choice choice = chunk.getChoices().get(0);
            GroqStreamChunk.Delta delta = choice.getDelta();
            if (delta != null && delta.getContent() != null && !delta.getContent().isEmpty()) {
                onChunk.accept(delta.getContent());
            }

            return "length".equals(choice.getFinishReason());
        } catch (Exception e) {
            log.debug("Skipping unparsable OpenRouter stream line: {}", line);
            return false;
        }
    }

    private boolean looksLikeCodeOrDocument(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("```")
                || lower.contains("<!doctype html")
                || lower.contains("<html")
                || lower.contains("class ")
                || lower.contains("function ");
    }

    private String suggestFileName(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase();
        if (lower.contains("<!doctype html") || lower.contains("<html")) {
            return "generated-page.html";
        }
        if (lower.contains("```java") || lower.contains("class ")) {
            return "GeneratedCode.java";
        }
        if (lower.contains("```javascript") || lower.contains("function ")) {
            return "script.js";
        }
        if (lower.contains("```css")) {
            return "styles.css";
        }
        return looksLikeCodeOrDocument(text) ? "generated-content.md" : null;
    }
}