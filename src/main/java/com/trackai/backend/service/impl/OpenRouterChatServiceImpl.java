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

    // ── Retry tuning ─────────────────────────────────────────────────────
    // Sirf genuine transient network errors par retry karte hain (connection
    // reset, dead pooled connection). 4xx/5xx application errors (balance,
    // rate limit, bad request) par KABHI retry nahi karte — wo real errors
    // hain, retry se sirf response slow hoga, fix nahi hoga.
    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(1);

    @Override
    public ChatResponse generateResponse(List<GroqMessage> messages, String modelId) {

        GroqResponse body = openRouterWebClient
                .post()
                .uri(properties.getChatEndpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequestBody(messages, resolveModel(modelId), false))
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
                .model(resolveModel(modelId))
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

        // ✅ Jab tak stream se ek bhi line nahi aayi, tab tak retry karna safe
        // hai. Ek baar chunk aana shuru ho gaya, uske baad retry nahi karte —
        // warna user ko wahi text dobara mil jayega (duplicate answer).
        AtomicBoolean streamStarted = new AtomicBoolean(false);

        openRouterWebClient
                .post()
                .uri(properties.getChatEndpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequestBody(messages, resolveModel(modelId), true))
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
                            handleLine(rawLine, onChunk);
                        },
                        onError,
                        onComplete);
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
            models.add(model);
        }

        return models;
    }

    private Map<String, Object> buildRequestBody(List<GroqMessage> messages, String modelId, boolean stream) {
        List<Map<String, String>> outMessages = new ArrayList<>();
        outMessages.add(message("system", SYSTEM_PROMPT));

        for (GroqMessage item : messages) {
            outMessages.add(message(item.getRole(), item.getContent()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);
        body.put("messages", outMessages);
        body.put("stream", stream);
        body.put("max_tokens", 4096);
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

    // ── Error classification ─────────────────────────────────────────────
    // OpenRouter error responses look like:
    // { "error": { "message": "...", "code": 402, "metadata": {...} } }
    // Hum body parse karke ek clean, human-readable message banate hain,
    // taaki "Connection reset" jaisa confusing text kabhi user tak na jaye
    // jab asal me balance/rate-limit ka issue ho.
    private Mono<Throwable> mapErrorResponse(ClientResponse response) {
        int statusCode = response.statusCode().value();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(rawBody -> {
                    // Full raw body WARN me daal rahe hain — agar metadata parsing
                    // kuch miss kare bhi to raw JSON seedha logs me mil jayega.
                    log.warn("OpenRouter returned HTTP {} — raw body: {}", statusCode, rawBody);
                    return new OpenRouterException(buildErrorMessage(statusCode, rawBody));
                });
    }

    private String buildErrorMessage(int statusCode, String rawBody) {
        String providerMessage = extractProviderMessage(rawBody);

        // 402 = Payment Required (OpenRouter uses this for insufficient credits)
        if (statusCode == 402
                || containsAny(providerMessage, "insufficient", "credit", "balance", "payment required")) {
            return "OPENROUTER_INSUFFICIENT_CREDITS: OpenRouter account/model par credits khatam ya insufficient hain. "
                    + "OpenRouter dashboard me balance check karein. Detail: " + providerMessage;
        }

        // 429 = Too Many Requests (common on free models under load)
        if (statusCode == 429 || containsAny(providerMessage, "rate limit", "too many requests")) {
            return "OPENROUTER_RATE_LIMIT: OpenRouter/model abhi rate-limited hai (free models par common hai). "
                    + "Thodi der baad phir try karein. Detail: " + providerMessage;
        }

        // 401/403 = auth issue with the OpenRouter API key
        if (statusCode == 401 || statusCode == 403) {
            return "OPENROUTER_AUTH_ERROR: OpenRouter API key invalid ya unauthorized hai. Detail: " + providerMessage;
        }

        // 503/502 = upstream model provider down/overloaded
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

            // ✅ Generic wrappers like "Provider returned error" hide the real
            // reason inside error.metadata — provider_name (kaunsa upstream
            // provider tha) aur raw (asli underlying error) dono nikaalte hain.
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

    // ── Transient network error detection ──────────────────────────────
    // Ye sirf "connection tut gaya" jaisi cheezon ke liye true hoga —
    // asli application errors (balance khatam, rate limit, bad key) ke liye
    // false, taaki unpar hum kabhi retry na karein.
    private boolean isTransientNetworkError(Throwable ex) {
        if (ex == null)
            return false;
        if (ex instanceof OpenRouterException)
            return false; // application-level error, don't retry
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

    private void handleLine(String rawLine, Consumer<String> onChunk) {
        if (rawLine == null) {
            return;
        }

        String line = rawLine.trim();
        if (line.isEmpty()) {
            return;
        }

        if (line.startsWith("data:")) {
            line = line.substring(5).trim();
        }

        if (line.isEmpty() || "[DONE]".equals(line)) {
            return;
        }

        try {
            GroqStreamChunk chunk = objectMapper.readValue(line, GroqStreamChunk.class);
            if (chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
                return;
            }

            GroqStreamChunk.Delta delta = chunk.getChoices().get(0).getDelta();
            if (delta != null && delta.getContent() != null && !delta.getContent().isEmpty()) {
                onChunk.accept(delta.getContent());
            }
        } catch (Exception e) {
            log.debug("Skipping unparsable OpenRouter stream line: {}", line);
        }
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
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