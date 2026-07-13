package com.trackai.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trackai.backend.config.GeminiResumeProperties;
import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.config.TokenProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.resume.*;
import com.trackai.backend.entity.ResumeMessage;
import com.trackai.backend.entity.ResumeProject;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.enums.ResumeStatus;
import com.trackai.backend.exception.RateLImitException;
import com.trackai.backend.exception.ResourceNotFoundException;
import com.trackai.backend.exception.ResumeProcessingException;
import com.trackai.backend.repository.ResumeMessageRepository;
import com.trackai.backend.repository.ResumeProjectRepository;
import com.trackai.backend.service.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeProjectRepository projectRepository;
    private final ResumeMessageRepository messageRepository;
    private final ResumeDocumentTextExtractor textExtractor;
    private final GeminiResumeClient geminiResumeClient;
    private final ResumePdfService resumePdfService;
    private final UserService userService;
    private final WalletService walletService;
    private final RedisRateLimitService redisRateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final TokenProperties tokenProperties;
    private final GeminiResumeProperties resumeProperties;
    private final ObjectMapper objectMapper;

    @Override
    public ResumeProjectResponse analyze(MultipartFile resume, String jobDescription, String model) {
        User user = userService.getCurrentUser();
        enforceRateLimit(user.getId());
        String safeJobDescription = validateJobDescription(jobDescription, false);
        String selectedModel = resolveModel(model, null);
        ResumeDocumentTextExtractor.ExtractedResume extracted = textExtractor.extract(resume);

        long cost = resumeCost(tokenProperties.getResumeAnalysis(), 30L);
        consume(user.getId(), cost, "Resume ATS analysis");

        try {
            JsonNode analysis = sanitizeAnalysis(geminiResumeClient.analyze(
                    extracted.text(), safeJobDescription, extracted.inlineDocument(), extracted.mimeType(), selectedModel));

            String storedText = extracted.text();
            if (storedText.isBlank()) {
                storedText = analysis.path("normalizedResumeText").asText("").trim();
            }
            if (storedText.isBlank()) {
                throw new ResumeProcessingException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No readable content could be recovered from this resume.");
            }

            ResumeProject project = ResumeProject.builder()
                    .userId(user.getId())
                    .originalFileName(extracted.fileName())
                    .sourceMimeType(extracted.mimeType())
                    .modelId(selectedModel)
                    .resumeText(limit(storedText, resumeProperties.getMaxResumeChars()))
                    .jobDescription(safeJobDescription)
                    .analysisJson(writeJson(analysis))
                    .status(ResumeStatus.ANALYZED)
                    .atsScore(score(analysis.path("overallScore")))
                    .matchScore(analysis.path("jobMatch").path("provided").asBoolean(false)
                            ? score(analysis.path("jobMatch").path("score"))
                            : null)
                    .build();

            return toResponse(projectRepository.save(project), true);
        } catch (RuntimeException ex) {
            refund(user.getId(), cost);
            throw ex;
        }
    }

    @Override
    public ResumeProjectResponse matchJob(String projectId, JobDescriptionRequest request) {
        User user = userService.getCurrentUser();
        enforceRateLimit(user.getId());
        ResumeProject project = ownedProject(projectId, user.getId());
        String jobDescription = validateJobDescription(request.getJobDescription(), true);
        String selectedModel = resolveModel(request.getModel(), project.getModelId());
        long cost = resumeCost(tokenProperties.getResumeMatch(), 20L);
        consume(user.getId(), cost, "Resume job match");

        try {
            JsonNode analysis = sanitizeAnalysis(geminiResumeClient.analyze(
                    currentResumeContext(project), jobDescription, null, project.getSourceMimeType(), selectedModel));

            project.setJobDescription(jobDescription);
            project.setModelId(selectedModel);
            project.setAnalysisJson(writeJson(analysis));
            project.setAtsScore(score(analysis.path("overallScore")));
            project.setMatchScore(score(analysis.path("jobMatch").path("score")));
            project.setGeneratedResumeJson(null);
            project.setStatus(ResumeStatus.ANALYZED);
            return toResponse(projectRepository.save(project), true);
        } catch (RuntimeException ex) {
            refund(user.getId(), cost);
            throw ex;
        }
    }

    @Override
    public ResumeChatResponse chat(String projectId, ResumeChatRequest request) {
        User user = userService.getCurrentUser();
        enforceRateLimit(user.getId());
        ResumeProject project = ownedProject(projectId, user.getId());
        String selectedModel = resolveModel(request.getModel(), project.getModelId());
        String userMessage = request.getMessage().trim();
        String history = buildHistory(projectId);
        long cost = resumeCost(tokenProperties.getResumeChat(), 5L);
        consume(user.getId(), cost, "Resume coach message");

        try {
            saveResumeMessage(projectId, "USER", userMessage);
            String answer = geminiResumeClient.chat(
                    currentResumeContext(project), readJson(project.getAnalysisJson()),
                    project.getJobDescription(), history, userMessage, selectedModel);
            saveResumeMessage(projectId, "ASSISTANT", answer);

            project.setModelId(selectedModel);
            projectRepository.save(project);
            return ResumeChatResponse.builder().message(answer).build();
        } catch (RuntimeException ex) {
            refund(user.getId(), cost);
            throw ex;
        }
    }

    @Override
    public Flux<String> streamChat(String projectId, ResumeChatRequest request) {
        User user = userService.getCurrentUser();
        enforceRateLimit(user.getId());
        ResumeProject project = ownedProject(projectId, user.getId());
        String selectedModel = resolveModel(request.getModel(), project.getModelId());
        String userMessage = request.getMessage().trim();
        String history = buildHistory(projectId);
        StringBuilder completedAnswer = new StringBuilder();
        long cost = resumeCost(tokenProperties.getResumeChat(), 5L);
        AtomicBoolean settled = new AtomicBoolean(false);
        consume(user.getId(), cost, "Resume coach message");
        saveResumeMessage(projectId, "USER", userMessage);

        try {
            return geminiResumeClient.chatStream(
                            currentResumeContext(project), readJson(project.getAnalysisJson()),
                            project.getJobDescription(), history, userMessage, selectedModel)
                    .doOnNext(completedAnswer::append)
                    .publishOn(Schedulers.boundedElastic())
                    .doOnComplete(() -> {
                        if (!settled.compareAndSet(false, true)) return;
                        String answer = completedAnswer.toString().trim();
                        if (answer.isBlank()) {
                            refund(user.getId(), cost);
                            return;
                        }
                        saveResumeMessage(projectId, "ASSISTANT", answer);
                        project.setModelId(selectedModel);
                        projectRepository.save(project);
                    })
                    .doOnError(error -> {
                        if (settled.compareAndSet(false, true)) refund(user.getId(), cost);
                    })
                    .doOnCancel(() -> {
                        if (settled.compareAndSet(false, true)) refund(user.getId(), cost);
                    });
        } catch (RuntimeException ex) {
            if (settled.compareAndSet(false, true)) refund(user.getId(), cost);
            throw ex;
        }
    }
    @Override
    public GeneratedResumeResponse generate(String projectId, GenerateResumeRequest request) {
        User user = userService.getCurrentUser();
        enforceRateLimit(user.getId());
        ResumeProject project = ownedProject(projectId, user.getId());
        String instructions = request == null ? "" : request.getInstructions();
        String requestedModel = request == null ? null : request.getModel();
        String selectedModel = resolveModel(requestedModel, project.getModelId());
        String safeInstructions = instructions == null ? "" : instructions.trim();
        long cost = resumeCost(tokenProperties.getResumeGenerate(), 40L);
        consume(user.getId(), cost, "ATS resume generation");

        try {
            if (!safeInstructions.isBlank()) {
                saveResumeMessage(projectId, "USER", safeInstructions);
            }

            JsonNode generated = geminiResumeClient.generateResume(
                    currentResumeContext(project), readJson(project.getAnalysisJson()),
                    project.getJobDescription(), safeInstructions, selectedModel);

            project.setGeneratedResumeJson(writeJson(generated));
            project.setModelId(selectedModel);
            project.setStatus(ResumeStatus.GENERATED);
            projectRepository.save(project);

            String confirmation = sameLanguageConfirmation(safeInstructions);
            saveResumeMessage(projectId, "ASSISTANT", confirmation);
            String fileName = downloadFileName(generated);
            return GeneratedResumeResponse.builder()
                    .projectId(project.getId())
                    .resume(generated)
                    .downloadUrl("/api/resumes/" + project.getId() + "/download")
                    .fileName(fileName)
                    .message(confirmation)
                    .build();
        } catch (RuntimeException ex) {
            refund(user.getId(), cost);
            throw ex;
        }
    }
    @Override
    public ResumeProjectResponse getProject(String projectId) {
        User user = userService.getCurrentUser();
        return toResponse(ownedProject(projectId, user.getId()), true);
    }

    @Override
    public List<ResumeProjectSummaryResponse> getProjects() {
        User user = userService.getCurrentUser();
        return projectRepository.findTop20ByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(project -> ResumeProjectSummaryResponse.builder()
                        .id(project.getId())
                        .fileName(project.getOriginalFileName())
                        .status(project.getStatus().name())
                        .modelId(effectiveModel(project))
                        .modelLabel(modelLabel(effectiveModel(project)))
                        .atsScore(project.getAtsScore())
                        .matchScore(project.getMatchScore())
                        .jobDescriptionProvided(project.getJobDescription() != null
                                && !project.getJobDescription().isBlank())
                        .updatedAt(project.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    public List<ResumeModelResponse> getModels() {
        String defaultModel = resumeProperties.getModel();
        return resumeProperties.availableModels().stream()
                .map(model -> ResumeModelResponse.builder()
                        .id(model.getId())
                        .label(model.getLabel())
                        .description(model.getDescription())
                        .tier(model.getTier())
                        .preview(model.isPreview())
                        .defaultModel(model.getId().equals(defaultModel))
                        .build())
                .toList();
    }

    @Override
    public ResumeDownload download(String projectId) {
        User user = userService.getCurrentUser();
        ResumeProject project = ownedProject(projectId, user.getId());
        if (project.getGeneratedResumeJson() == null || project.getGeneratedResumeJson().isBlank()) {
            throw new ResumeProcessingException(HttpStatus.CONFLICT,
                    "Generate the ATS resume before downloading it.");
        }
        JsonNode generated = readJson(project.getGeneratedResumeJson());
        return new ResumeDownload(resumePdfService.create(generated), downloadFileName(generated));
    }

    @Override
    @Transactional
    public void delete(String projectId) {
        User user = userService.getCurrentUser();
        ResumeProject project = ownedProject(projectId, user.getId());
        messageRepository.deleteByProjectId(projectId);
        projectRepository.delete(project);
    }

    private ResumeProject ownedProject(String projectId, String userId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume project not found."));
    }

    private ResumeProjectResponse toResponse(ResumeProject project, boolean includeMessages) {
        JsonNode generated = project.getGeneratedResumeJson() == null ? null
                : readJson(project.getGeneratedResumeJson());
        return ResumeProjectResponse.builder()
                .id(project.getId())
                .fileName(project.getOriginalFileName())
                .status(project.getStatus().name())
                        .modelId(effectiveModel(project))
                        .modelLabel(modelLabel(effectiveModel(project)))
                .atsScore(project.getAtsScore())
                .matchScore(project.getMatchScore())
                .jobDescriptionProvided(project.getJobDescription() != null
                        && !project.getJobDescription().isBlank())
                .analysis(readJson(project.getAnalysisJson()))
                .generatedResume(generated)
                .messages(includeMessages ? recentMessages(project.getId()).stream()
                        .map(message -> ResumeMessageResponse.builder()
                                .id(message.getId())
                                .role(message.getRole())
                                .content(message.getContent())
                                .createdAt(message.getCreatedAt())
                                .build())
                        .toList() : List.of())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .downloadUrl(generated == null ? null : "/api/resumes/" + project.getId() + "/download")
                .build();
    }

    private List<ResumeMessage> recentMessages(String projectId) {
        int limit = Math.max(20, Math.min(200, resumeProperties.getChatHistoryMessages()));
        List<ResumeMessage> messages = new ArrayList<>(
                messageRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, limit)));
        Collections.reverse(messages);
        return messages;
    }

    private String buildHistory(String projectId) {
        String history = recentMessages(projectId).stream()
                .map(message -> message.getRole() + ": " + limit(message.getContent(), 2_000))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        int maxChars = Math.max(4_000, resumeProperties.getChatHistoryMaxChars());
        return history.length() <= maxChars ? history : history.substring(history.length() - maxChars);
    }

    private String currentResumeContext(ResumeProject project) {
        String original = limit(project.getResumeText(), resumeProperties.getMaxResumeChars());
        String generated = project.getGeneratedResumeJson();
        if (generated == null || generated.isBlank()) return original;
        return "CURRENT DOWNLOADABLE RESUME JSON:\n"
                + limit(generated, resumeProperties.getMaxResumeChars())
                + "\n\nORIGINAL RESUME SOURCE:\n" + original;
    }
    private void saveResumeMessage(String projectId, String role, String content) {
        if (content == null || content.isBlank()) return;
        messageRepository.save(ResumeMessage.builder()
                .projectId(projectId)
                .role(role)
                .content(content.trim())
                .build());
    }

    private String sameLanguageConfirmation(String instructions) {
        if (instructions != null && instructions.matches(".*[\\u0900-\\u097F].*")) {
            return "Aapka improved ATS-friendly resume taiyar hai. Neeche Download PDF button se ise download kijiye.";
        }
        String lower = instructions == null ? "" : instructions.toLowerCase();
        if (lower.matches(".*\\b(bhai|mera|meri|mujhe|karo|banao|banana|chahiye|resume bana)\\b.*")) {
            return "Aapka improved ATS-friendly resume ready hai. Neeche Download PDF button se download kar sakte hain.";
        }
        return "Your improved ATS-friendly resume is ready. Use the Download PDF button below.";
    }

    private JsonNode sanitizeAnalysis(JsonNode source) {
        if (!(source instanceof ObjectNode analysis)) {
            throw new ResumeProcessingException(HttpStatus.BAD_GATEWAY,
                    "Resume AI returned an invalid analysis.");
        }
        analysis.put("overallScore", score(analysis.path("overallScore")));
        JsonNode categories = analysis.path("categoryScores");
        if (categories instanceof ObjectNode object) {
            for (String key : List.of("formatting", "content", "impact", "skills", "readability")) {
                object.put(key, score(object.path(key)));
            }
        }
        JsonNode jobMatch = analysis.path("jobMatch");
        if (jobMatch instanceof ObjectNode object) {
            object.put("score", score(object.path("score")));
        }
        return analysis;
    }

    private int score(JsonNode node) {
        return Math.max(0, Math.min(100, node.asInt(0)));
    }

    private String resolveModel(String requestedModel, String storedModel) {
        String candidate = requestedModel == null || requestedModel.isBlank()
                ? storedModel
                : requestedModel;
        String resolved = resumeProperties.resolveModel(candidate);
        if (resolved == null || resolved.isBlank()) {
            throw new ResumeProcessingException(HttpStatus.BAD_REQUEST,
                    "Selected Gemini model is not enabled for Resume AI.");
        }
        return resolved;
    }

    private String effectiveModel(ResumeProject project) {
        String resolved = resumeProperties.resolveModel(project.getModelId());
        return resolved == null ? resumeProperties.getModel() : resolved;
    }

    private String modelLabel(String modelId) {
        return resumeProperties.availableModels().stream()
                .filter(model -> model.getId().equals(modelId))
                .map(GeminiResumeProperties.ModelInfo::getLabel)
                .findFirst()
                .orElse(modelId);
    }

    private String validateJobDescription(String value, boolean required) {
        String normalized = value == null ? "" : value.trim();
        if (required && normalized.isBlank()) {
            throw new ResumeProcessingException(HttpStatus.BAD_REQUEST, "Job description is required.");
        }
        if (normalized.length() > resumeProperties.getMaxJobDescriptionChars()) {
            throw new ResumeProcessingException(HttpStatus.BAD_REQUEST,
                    "Job description must be 20000 characters or less.");
        }
        return normalized.isBlank() ? null : normalized;
    }

    private void enforceRateLimit(String userId) {
        RateLimitProperties.Limit limit = rateLimitProperties.getResume();
        long capacity = limit.getCapacity() > 0 ? limit.getCapacity() : 8;
        long refill = limit.getRefillTokens() > 0 ? limit.getRefillTokens() : 8;
        long minutes = limit.getRefillMinutes() > 0 ? limit.getRefillMinutes() : 1;
        RateLimitResponse response = redisRateLimitService.allowRequest(
                "resume:" + userId, capacity, refill, minutes);
        if (!response.isAllowed()) {
            throw new RateLImitException(response.getMessage());
        }
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new ResumeProcessingException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stored resume data could not be read.", ex);
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ResumeProcessingException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Resume data could not be saved.", ex);
        }
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private long resumeCost(Long configured, long defaultCost) {
        Long legacy = tokenProperties.getResume();
        long value = configured != null ? configured : (legacy != null ? legacy : defaultCost);
        return Math.max(0L, value);
    }

    private void consume(String userId, long cost, String description) {
        if (cost > 0) walletService.consumeTokens(userId, cost, FeatureType.RESUME, description);
    }
    private void refund(String userId, long cost) {
        if (cost <= 0) return;
        try {
            walletService.addTokens(userId, cost, FeatureType.RESUME, "Refund failed resume analysis");
        } catch (Exception refundError) {
            log.error("Resume analysis refund failed for user {}", userId, refundError);
        }
    }

    private String downloadFileName(JsonNode resume) {
        String name = resume.path("name").asText("Candidate")
                .replaceAll("[^A-Za-z0-9 -]", "").trim()
                .replaceAll("\\s+", "_");
        if (name.isBlank())
            name = "Candidate";
        return name + "_ATS_Resume.pdf";
    }
}
