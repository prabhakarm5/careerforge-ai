package com.trackai.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.config.GeminiResumeProperties;
import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.config.TokenProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.interview.*;
import com.trackai.backend.entity.*;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.enums.InterviewStatus;
import com.trackai.backend.exception.InterviewException;
import com.trackai.backend.exception.RateLImitException;
import com.trackai.backend.repository.InterviewSessionRepository;
import com.trackai.backend.repository.InterviewTurnRepository;
import com.trackai.backend.repository.ResumeProjectRepository;
import com.trackai.backend.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {
    private final InterviewSessionRepository sessionRepository;
    private final InterviewTurnRepository turnRepository;
    private final ResumeProjectRepository resumeRepository;
    private final GeminiResumeClient geminiClient;
    private final InterviewPersistenceService persistenceService;
    private final UserService userService;
    private final WalletService walletService;
    private final RedisRateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final TokenProperties tokenProperties;
    private final GeminiResumeProperties geminiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public InterviewSessionResponse start(StartInterviewRequest request) {
        User user = userService.getCurrentUser();
        enforceRateLimit(user.getId());
        ResumeProject resume = optionalResume(request.getResumeProjectId(), user.getId());
        String resumeContext = resumeContext(resume, request.getResumeContext());
        String model = resolveModel(request.getModel(), resume == null ? null : resume.getModelId());
        long cost = Math.max(0L, value(tokenProperties.getInterviewStart(), 10L));
        consume(user.getId(), cost, "Interview practice start");

        try {
            JsonNode generated = geminiClient.generateInterviewQuestion(
                    resumeContext, normalize(request.getJobDescription()), request.getRole().trim(),
                    normalize(request.getCompany()), request.getType().name(), request.getDifficulty().name(),
                    "", 1, model);
            InterviewSession session = InterviewSession.builder()
                    .userId(user.getId())
                    .resumeProjectId(resume == null ? null : resume.getId())
                    .resumeContext(resumeContext)
                    .role(request.getRole().trim())
                    .company(normalize(request.getCompany()))
                    .jobDescription(normalize(request.getJobDescription()))
                    .type(request.getType())
                    .difficulty(request.getDifficulty())
                    .status(InterviewStatus.IN_PROGRESS)
                    .modelId(model)
                    .totalQuestions(request.getQuestionCount())
                    .currentQuestionNumber(1)
                    .currentQuestion(requiredText(generated, "question", "Tell me about your relevant experience."))
                    .currentFocus(generated.path("focus").asText("Role fit"))
                    .build();
            persistenceService.saveSession(session);
            return toResponse(session, List.of());
        } catch (RuntimeException ex) {
            refund(user.getId(), cost, "Refund failed interview start");
            throw ex;
        }
    }

    @Override
    public InterviewSessionResponse answer(String sessionId, InterviewAnswerRequest request) {
        User user = userService.getCurrentUser();
        enforceRateLimit(user.getId());
        InterviewSession session = ownedSession(sessionId, user.getId());
        if (session.getStatus() == InterviewStatus.COMPLETED) {
            throw new InterviewException(HttpStatus.CONFLICT, "This interview is already complete.");
        }
        ResumeProject resume = optionalResume(session.getResumeProjectId(), user.getId());
        String resumeContext = resumeContext(resume, session.getResumeContext());
        List<InterviewTurn> previous = new ArrayList<>(
                turnRepository.findBySessionIdOrderByQuestionNumberAsc(sessionId));
        boolean finalAnswer = session.getCurrentQuestionNumber() >= session.getTotalQuestions();
        long cost = Math.max(0L, value(tokenProperties.getInterviewAnswer(), 10L));
        consume(user.getId(), cost, "Interview answer evaluation");

        try {
            JsonNode evaluation = geminiClient.evaluateInterviewAnswer(
                    resumeContext, session.getJobDescription(), session.getRole(), session.getCompany(),
                    session.getType().name(), session.getDifficulty().name(), transcript(previous),
                    session.getCurrentQuestion(), request.getAnswer().trim(), finalAnswer, session.getModelId());
            int score = calculateStrictScore(evaluation, request.getAnswer());
            InterviewTurn turn = InterviewTurn.builder()
                    .sessionId(session.getId())
                    .questionNumber(session.getCurrentQuestionNumber())
                    .question(session.getCurrentQuestion())
                    .questionFocus(session.getCurrentFocus())
                    .answer(request.getAnswer().trim())
                    .score(score)
                    .feedback(requiredText(evaluation, "feedback", "Answer evaluated."))
                    .strengthsJson(writeStrings(evaluation.path("strengths")))
                    .improvementsJson(writeStrings(evaluation.path("improvements")))
                    .idealAnswer(requiredText(evaluation, "idealAnswer", "Use a clear example and explain your impact."))
                    .build();

            int scoreTotal = previous.stream().mapToInt(InterviewTurn::getScore).sum() + score;
            int answered = previous.size() + 1;
            session.setOverallScore(Math.round((float) scoreTotal / answered));
            session.setSummary(evaluation.path("sessionSummary").asText(""));
            if (finalAnswer) {
                session.setStatus(InterviewStatus.COMPLETED);
                session.setCurrentQuestion("");
                session.setCurrentFocus("");
            } else {
                session.setCurrentQuestionNumber(session.getCurrentQuestionNumber() + 1);
                session.setCurrentQuestion(requiredText(evaluation, "nextQuestion",
                        "Tell me about another relevant challenge you solved."));
                session.setCurrentFocus(evaluation.path("nextFocus").asText("Adaptive follow-up"));
            }
            persistenceService.saveEvaluation(session, turn);
            previous.add(turn);
            return toResponse(session, previous);
        } catch (RuntimeException ex) {
            refund(user.getId(), cost, "Refund failed interview evaluation");
            throw ex;
        }
    }

    @Override
    public InterviewSessionResponse get(String sessionId) {
        String userId = userService.getCurrentUser().getId();
        InterviewSession session = ownedSession(sessionId, userId);
        return toResponse(session, turnRepository.findBySessionIdOrderByQuestionNumberAsc(sessionId));
    }

    @Override
    public List<InterviewSummaryResponse> history() {
        String userId = userService.getCurrentUser().getId();
        return sessionRepository.findTop30ByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(session -> InterviewSummaryResponse.builder()
                        .id(session.getId()).role(session.getRole()).company(session.getCompany())
                        .type(session.getType().name()).difficulty(session.getDifficulty().name())
                        .status(session.getStatus().name()).overallScore(session.getOverallScore())
                        .answeredQuestions(session.getStatus() == InterviewStatus.COMPLETED
                                ? session.getTotalQuestions() : Math.max(0, session.getCurrentQuestionNumber() - 1))
                        .totalQuestions(session.getTotalQuestions()).updatedAt(session.getUpdatedAt()).build())
                .toList();
    }

    @Override
    public void delete(String sessionId) {
        User user = userService.getCurrentUser();
        persistenceService.delete(ownedSession(sessionId, user.getId()));
    }

    private InterviewSessionResponse toResponse(InterviewSession session, List<InterviewTurn> turns) {
        return InterviewSessionResponse.builder()
                .id(session.getId()).resumeProjectId(session.getResumeProjectId())
                .role(session.getRole()).company(session.getCompany()).jobDescription(session.getJobDescription())
                .type(session.getType().name()).difficulty(session.getDifficulty().name())
                .status(session.getStatus().name()).modelId(session.getModelId())
                .modelLabel(modelLabel(session.getModelId())).totalQuestions(session.getTotalQuestions())
                .currentQuestionNumber(session.getCurrentQuestionNumber())
                .currentQuestion(session.getCurrentQuestion()).currentFocus(session.getCurrentFocus())
                .overallScore(session.getOverallScore()).summary(session.getSummary())
                .completed(session.getStatus() == InterviewStatus.COMPLETED)
                .turns(turns.stream().map(this::toTurn).toList())
                .createdAt(session.getCreatedAt()).updatedAt(session.getUpdatedAt()).build();
    }

    private InterviewTurnResponse toTurn(InterviewTurn turn) {
        return InterviewTurnResponse.builder()
                .id(turn.getId()).questionNumber(turn.getQuestionNumber()).question(turn.getQuestion())
                .questionFocus(turn.getQuestionFocus()).answer(turn.getAnswer()).score(turn.getScore())
                .feedback(turn.getFeedback()).strengths(readStrings(turn.getStrengthsJson()))
                .improvements(readStrings(turn.getImprovementsJson())).idealAnswer(turn.getIdealAnswer())
                .createdAt(turn.getCreatedAt()).build();
    }

    private InterviewSession ownedSession(String id, String userId) {
        return sessionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new InterviewException(HttpStatus.NOT_FOUND, "Interview session not found."));
    }

    private ResumeProject optionalResume(String id, String userId) {
        if (id == null || id.isBlank()) return null;
        return resumeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new InterviewException(HttpStatus.NOT_FOUND, "Selected resume was not found."));
    }

    private String resumeContext(ResumeProject resume, String uploadedContext) {
        if (resume != null) {
            String value = resume.getGeneratedResumeJson() != null && !resume.getGeneratedResumeJson().isBlank()
                    ? resume.getGeneratedResumeJson() : resume.getResumeText();
            return limit(value, geminiProperties.getMaxResumeChars());
        }
        return limit(normalize(uploadedContext), Math.min(20_000, geminiProperties.getMaxResumeChars()));
    }

    private String transcript(List<InterviewTurn> turns) {
        String value = turns.stream().map(turn -> "Q" + turn.getQuestionNumber() + ": " + turn.getQuestion()
                        + "\nA: " + limit(turn.getAnswer(), 2_000) + "\nScore: " + turn.getScore())
                .reduce((left, right) -> left + "\n\n" + right).orElse("");
        return limit(value, 18_000);
    }

    private String resolveModel(String requested, String stored) {
        String candidate = requested == null || requested.isBlank() ? stored : requested;
        String resolved = geminiProperties.resolveModel(candidate);
        if (resolved == null || resolved.isBlank()) {
            throw new InterviewException(HttpStatus.BAD_REQUEST, "Selected Gemini model is not enabled.");
        }
        return resolved;
    }

    private String modelLabel(String id) {
        return geminiProperties.availableModels().stream().filter(model -> model.getId().equals(id))
                .map(GeminiResumeProperties.ModelInfo::getLabel).findFirst().orElse(id);
    }

    private void enforceRateLimit(String userId) {
        RateLimitProperties.Limit limit = rateLimitProperties.getInterview();
        long capacity = limit.getCapacity() > 0 ? limit.getCapacity() : 10;
        long refill = limit.getRefillTokens() > 0 ? limit.getRefillTokens() : 10;
        long minutes = limit.getRefillMinutes() > 0 ? limit.getRefillMinutes() : 1;
        RateLimitResponse response = rateLimitService.allowRequest("interview:" + userId, capacity, refill, minutes);
        if (!response.isAllowed()) throw new RateLImitException(response.getMessage());
    }

    private void consume(String userId, long cost, String description) {
        if (cost > 0) walletService.consumeTokens(userId, cost, FeatureType.INTERVIEW, description);
    }

    private void refund(String userId, long cost, String description) {
        if (cost <= 0) return;
        try {
            walletService.addTokens(userId, cost, FeatureType.INTERVIEW, description);
        } catch (Exception error) {
            log.error("Interview credit refund failed for user {}", userId, error);
        }
    }

    private long value(Long configured, long fallback) {
        return configured == null ? fallback : configured;
    }

    private String requiredText(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? fallback : value;
    }

    private int calculateStrictScore(JsonNode evaluation, String answer) {
        JsonNode rubric = evaluation.path("rubric");
        int score;
        if (rubric.isObject()) {
            int relevance = rubricScore(rubric, "relevance");
            int correctness = rubricScore(rubric, "correctness");
            int evidence = rubricScore(rubric, "evidence");
            int structure = rubricScore(rubric, "structure");
            int communication = rubricScore(rubric, "communication");
            int roleFit = rubricScore(rubric, "roleFit");
            score = Math.round((relevance * 0.20f + correctness * 0.20f + evidence * 0.20f
                    + structure * 0.15f + communication * 0.10f + roleFit * 0.15f) * 10f);
        } else {
            score = evaluation.path("score").asInt(0);
        }

        String normalizedAnswer = answer == null ? "" : answer.trim();
        int wordCount = normalizedAnswer.isBlank() ? 0 : normalizedAnswer.split("\\s+").length;
        if (wordCount < 8) score = Math.min(score, 25);
        else if (wordCount < 20) score = Math.min(score, 45);
        else if (wordCount < 40) score = Math.min(score, 65);
        return Math.max(0, Math.min(100, score));
    }

    private int rubricScore(JsonNode rubric, String field) {
        return Math.max(0, Math.min(10, rubric.path(field).asInt(0)));
    }

    private String writeStrings(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node.isArray() ? node : objectMapper.createArrayNode());
        } catch (Exception ex) {
            throw new InterviewException(HttpStatus.INTERNAL_SERVER_ERROR, "Interview feedback could not be saved.", ex);
        }
    }

    private List<String> readStrings(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
