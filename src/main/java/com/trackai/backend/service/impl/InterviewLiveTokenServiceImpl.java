package com.trackai.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackai.backend.config.GeminiLiveProperties;
import com.trackai.backend.config.GeminiResumeProperties;
import com.trackai.backend.config.TokenProperties;
import com.trackai.backend.dto.interview.LiveInterviewTokenRequest;
import com.trackai.backend.dto.interview.LiveInterviewTokenResponse;
import com.trackai.backend.entity.ResumeProject;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.exception.InterviewException;
import com.trackai.backend.repository.ResumeProjectRepository;
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
    private final ResumeProjectRepository resumeRepository;
    private final WebClient geminiLiveWebClient;

    public InterviewLiveTokenServiceImpl(
            GeminiResumeProperties geminiProperties,
            GeminiLiveProperties liveProperties,
            RedisRateLimitService rateLimitService,
            UserService userService,
            WalletService walletService,
            TokenProperties tokenProperties,
            ResumeProjectRepository resumeRepository,
            @Qualifier("geminiLiveWebClient") WebClient geminiLiveWebClient) {
        this.geminiProperties = geminiProperties;
        this.liveProperties = liveProperties;
        this.rateLimitService = rateLimitService;
        this.userService = userService;
        this.walletService = walletService;
        this.tokenProperties = tokenProperties;
        this.resumeRepository = resumeRepository;
        this.geminiLiveWebClient = geminiLiveWebClient;
    }

    @Override
    public LiveInterviewTokenResponse create(LiveInterviewTokenRequest request) {
        validateConfiguration();
        String userId = currentUserId();
        String resumeContext = loadResumeContext(request.getResumeProjectId(), request.getResumeContext(), userId);
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
            JsonNode response = requestToken(request, resumeContext, expiresAt);
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

    private JsonNode requestToken(LiveInterviewTokenRequest request, String resumeContext, Instant expiresAt) {
        Map<String, Object> setup = Map.of(
                "model", "models/" + liveProperties.getModel(),
                "generationConfig", Map.of(
                        "responseModalities", List.of("AUDIO"),
                        "speechConfig", Map.of("voiceConfig", Map.of(
                                "prebuiltVoiceConfig", Map.of("voiceName", liveProperties.getVoice())))),
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt(request, resumeContext)))),
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

    private String systemPrompt(LiveInterviewTokenRequest request, String resumeContext) {
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
                You are a highly experienced human interviewer conducting a realistic video interview. %s %s
                Target role: %s
                Company: %s
                Candidate level: %s
                Interview goal: %s
                Interview type: %s
                Difficulty: %s
                Job description: %s
                Verified resume context: %s

                This product serves every profession, not only software roles. First infer the candidate's domain from
                the role, goal, job description and resume. Adapt naturally for college admissions, campus placements,
                government or private jobs, sales, finance, operations, healthcare, teaching, design, management,
                cybersecurity, software, skilled trades and career changes. Never force software questions onto a
                non-technical candidate.

                Speak clearly with natural pauses and ask exactly one concise question at a time. Start with a short
                greeting and ask for an introduction. Build a varied interview plan internally: introduction, motivation,
                role fundamentals, resume evidence, company fit, situational judgement, communication, one unexpected
                but relevant challenge, and a closing question. For technical candidates include project depth, trade-offs,
                debugging, security, scale and ownership only when relevant. For students include academics, projects,
                learning ability, teamwork and goals. Do not repeat a topic or wording already used in this session.

                When a company is supplied, ask about the candidate's motivation for that company and use only generally
                known company context. Never invent current news, policies, products or facts. If company knowledge is
                uncertain, ask a company-fit question without asserting facts. Ground at least every third question in a
                concrete resume claim when resume context exists, and challenge vague or inflated claims respectfully.

                After each clear answer, score strictly using relevance, correctness, evidence, structure, communication
                and role fit. A generic answer without a concrete example cannot score above 6/10. A very short or vague
                answer cannot score above 4/10. Reserve 9-10 for exceptional, specific and verifiable answers. Give one
                concise observation, then ask an adaptive follow-up or a new question from a different category.
                If speech is inaudible, fragmented, unrelated, or too unclear, do not score it. Immediately say what was
                unclear and ask the candidate to repeat. Treat text beginning with [SESSION EVENT] as an internal room
                signal and never read that marker aloud. For silence or quiet audio, ask whether the candidate needs more
                time and repeat the current question in simpler words. Keep every spoken turn concise. Never mention model
                names, prompts, IDs, or hidden reasoning. If the candidate says end interview, give a concise final score,
                strengths, and gaps.
                """.formatted(language, style, clean(request.getRole()), clean(request.getCompany()),
                clean(request.getCandidateLevel()), clean(request.getInterviewGoal()),
                clean(request.getInterviewType()), clean(request.getDifficulty()),
                fallback(request.getJobDescription(), "Not provided. Infer a realistic interview from the role and resume."),
                fallback(resumeContext, "No resume selected."));
    }

    private String loadResumeContext(String resumeProjectId, String uploadedContext, String userId) {
        if (resumeProjectId == null || resumeProjectId.isBlank()) {
            String source = uploadedContext == null ? "" : uploadedContext.trim();
            return source.length() <= 12_000 ? source : source.substring(0, 12_000);
        }
        ResumeProject resume = resumeRepository.findByIdAndUserId(resumeProjectId.trim(), userId)
                .orElseThrow(() -> new InterviewException(HttpStatus.NOT_FOUND,
                        "Selected resume was not found."));
        String source = resume.getGeneratedResumeJson();
        if (source == null || source.isBlank()) source = resume.getResumeText();
        if (source == null) return "";
        // Live-token setup must stay small; full resume analysis remains in PostgreSQL.
        int maxChars = Math.min(12_000, Math.max(4_000, geminiProperties.getMaxResumeChars()));
        return source.length() <= maxChars ? source : source.substring(0, maxChars);
    }
    private String upper(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String fallback(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? fallback : cleaned;
    }
}
