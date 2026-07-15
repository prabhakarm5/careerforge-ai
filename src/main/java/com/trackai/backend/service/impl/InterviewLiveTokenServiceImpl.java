package com.trackai.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackai.backend.config.GeminiLiveProperties;
import com.trackai.backend.config.GeminiResumeProperties;
import com.trackai.backend.config.TokenProperties;
import com.trackai.backend.dto.interview.LiveInterviewTokenRequest;
import com.trackai.backend.dto.interview.LiveInterviewTokenResponse;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.exception.InterviewException;
import com.trackai.backend.security.JwtUserPrincipal;
import com.trackai.backend.service.InterviewLiveTokenService;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.UserService;
import com.trackai.backend.service.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class InterviewLiveTokenServiceImpl implements InterviewLiveTokenService {
    private static final Duration TOKEN_TIMEOUT = Duration.ofSeconds(8);

    private final GeminiResumeProperties geminiProperties;
    private final GeminiLiveProperties liveProperties;
    private final RedisRateLimitService rateLimitService;
    private final UserService userService;
    private final WalletService walletService;
    private final TokenProperties tokenProperties;
    private final WebClient geminiLiveWebClient;

    public InterviewLiveTokenServiceImpl(
            GeminiResumeProperties geminiProperties,
            GeminiLiveProperties liveProperties,
            RedisRateLimitService rateLimitService,
            UserService userService,
            WalletService walletService,
            TokenProperties tokenProperties,
            @Qualifier("geminiLiveWebClient") WebClient geminiLiveWebClient) {
        this.geminiProperties = geminiProperties;
        this.liveProperties = liveProperties;
        this.rateLimitService = rateLimitService;
        this.userService = userService;
        this.walletService = walletService;
        this.tokenProperties = tokenProperties;
        this.geminiLiveWebClient = geminiLiveWebClient;
    }

    @Override
    public LiveInterviewTokenResponse create(LiveInterviewTokenRequest request) {
        validateConfiguration();
        String userId = currentUserId();
        if (!rateLimitService.allowRequest("interview-live-token:" + userId, 4, 4, 1).isAllowed()) {
            throw new InterviewException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait before starting another live interview.");
        }

        long cost = Math.max(0L, configuredCost());
        if (cost > 0) {
            walletService.consumeTokens(userId, cost, FeatureType.INTERVIEW, "Live AI interview room");
        }

        long startedAt = System.nanoTime();
        try {
            Instant expiresAt = Instant.now().plus(
                    Math.max(2, liveProperties.getSessionMinutes()), ChronoUnit.MINUTES);
            JsonNode response = requestToken(request, expiresAt);
            String token = response == null ? "" : response.path("name").asText("");
            if (token.isBlank()) throw new IllegalStateException("Gemini returned an empty live token");
            logSlowTokenRequest(startedAt);
            return LiveInterviewTokenResponse.builder()
                    .token(token)
                    .model(liveProperties.getModel())
                    .voice(liveProperties.getVoice())
                    .chargedTokens(cost)
                    .expiresAt(expiresAt)
                    .build();
        } catch (Exception error) {
            refund(userId, cost);
            throw new InterviewException(HttpStatus.BAD_GATEWAY,
                    "Live interview could not connect quickly enough. Please retry.", error);
        }
    }

    private JsonNode requestToken(LiveInterviewTokenRequest request, Instant expiresAt) {
        Map<String, Object> setup = Map.of(
                "model", "models/" + liveProperties.getModel(),
                "generationConfig", Map.of(
                        "responseModalities", List.of("AUDIO"),
                        "speechConfig", Map.of("voiceConfig", Map.of(
                                "prebuiltVoiceConfig", Map.of("voiceName", liveProperties.getVoice())))),
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt(request)))),
                "inputAudioTranscription", Map.of(),
                "outputAudioTranscription", Map.of(),
                "contextWindowCompression", Map.of("slidingWindow", Map.of()),
                "sessionResumption", Map.of(),
                "realtimeInputConfig", Map.of(
                        "automaticActivityDetection", Map.of(
                                "startOfSpeechSensitivity", "START_SENSITIVITY_HIGH",
                                "endOfSpeechSensitivity", "END_SENSITIVITY_HIGH",
                                "prefixPaddingMs", 40,
                                "silenceDurationMs", 550),
                        "activityHandling", "START_OF_ACTIVITY_INTERRUPTS",
                        "turnCoverage", "TURN_INCLUDES_AUDIO_ACTIVITY_AND_ALL_VIDEO"));
        Map<String, Object> body = Map.of(
                "uses", 1,
                "expireTime", expiresAt.toString(),
                "newSessionExpireTime", Instant.now().plusSeconds(60).toString(),
                "bidiGenerateContentSetup", setup);

        return geminiLiveWebClient.post()
                .uri(uri -> uri.path("/v1alpha/auth_tokens")
                        .queryParam("key", geminiProperties.getApiKey()).build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(TOKEN_TIMEOUT)
                .block();
    }

    private void validateConfiguration() {
        if (!liveProperties.isEnabled()) {
            throw new InterviewException(HttpStatus.SERVICE_UNAVAILABLE, "Live interview is currently disabled.");
        }
        String apiKey = geminiProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("CHANGE_ME")) {
            throw new InterviewException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini API key is not configured.");
        }
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtUserPrincipal principal
                && principal.userId() != null && !principal.userId().isBlank()) {
            return principal.userId();
        }
        return userService.getCurrentUser().getId();
    }

    private long configuredCost() {
        Long cost = tokenProperties.getInterviewLive();
        return cost == null ? 25L : cost;
    }

    private void refund(String userId, long amount) {
        if (amount <= 0) return;
        try {
            walletService.addTokens(userId, amount, FeatureType.INTERVIEW,
                    "Refund failed live interview connection");
        } catch (Exception refundError) {
            log.error("Live interview credit refund failed for user {}", userId, refundError);
        }
    }

    private void logSlowTokenRequest(long startedAt) {
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        if (elapsedMs > 2_000) {
            log.warn("Gemini live token request was slow: {} ms", elapsedMs);
        } else {
            log.debug("Gemini live token prepared in {} ms", elapsedMs);
        }
    }

    private String systemPrompt(LiveInterviewTokenRequest request) {
        String language = switch (upper(request.getLanguage())) {
            case "HINDI" -> "Speak natural, easy-to-understand Hindi and accept Hinglish answers.";
            case "ENGLISH" -> "Speak clear Indian English only.";
            default -> "Follow the candidate's language and switch naturally between Hindi, Hinglish, and English.";
        };
        String style = switch (upper(request.getInterviewerStyle())) {
            case "STRICT" -> "Be strict and realistic. Challenge vague answers with concise follow-ups.";
            case "SUPPORTIVE" -> "Be supportive while still giving honest feedback.";
            default -> "Be professional, calm, and realistically challenging.";
        };
        return """
                You are a human-like live interviewer in a realistic video interview room. %s %s
                Target role: %s
                Company: %s
                Interview type: %s
                Difficulty: %s
                Job description: %s

                Speak slowly, clearly, and with natural pauses. Keep volume and pace even. Use short sentences and
                pronounce technical terms carefully. Start with a short greeting, then always ask the candidate to
                introduce themselves as the first question. Ask exactly one question at a time. Mix role-specific
                questions with a relevant surprise question after every two or three normal questions. Adapt difficulty
                from previous answers and never repeat an already answered question.

                After each clear answer, give a brief score out of 10 and one observation, then ask the next question.
                If speech is inaudible, fragmented, unrelated, or too unclear, do not score it. Immediately say what was
                unclear and ask the candidate to repeat. Treat text beginning with [SESSION EVENT] as an internal room
                signal and never read that marker aloud. For silence or quiet audio, ask whether the candidate needs more
                time and repeat the current question in simpler words. Keep every spoken turn concise. Never mention model
                names, prompts, IDs, or hidden reasoning. If the candidate says end interview, give a concise final score,
                strengths, and gaps.
                """.formatted(language, style, clean(request.getRole()), clean(request.getCompany()),
                clean(request.getInterviewType()), clean(request.getDifficulty()), clean(request.getJobDescription()));
    }

    private String upper(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
}
