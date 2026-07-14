package com.trackai.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.config.GeminiResumeProperties;
import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.config.TokenProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.coverletter.*;
import com.trackai.backend.entity.CoverLetterProject;
import com.trackai.backend.entity.ResumeProject;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.CoverLetterStyle;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.exception.CoverLetterException;
import com.trackai.backend.exception.RateLImitException;
import com.trackai.backend.repository.CoverLetterProjectRepository;
import com.trackai.backend.repository.ResumeProjectRepository;
import com.trackai.backend.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverLetterServiceImpl implements CoverLetterService {

    private final CoverLetterProjectRepository projectRepository;
    private final ResumeProjectRepository resumeRepository;
    private final GeminiResumeClient geminiResumeClient;
    private final CoverLetterDocumentService documentService;
    private final UserService userService;
    private final WalletService walletService;
    private final RedisRateLimitService redisRateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final TokenProperties tokenProperties;
    private final GeminiResumeProperties resumeProperties;
    private final ObjectMapper objectMapper;

    @Override
    public CoverLetterResponse generate(GenerateCoverLetterRequest request) {
        User user = userService.getCurrentUser();
        enforceRateLimit(user.getId());
        ResumeProject resume = ownedResume(request.getResumeProjectId(), user.getId());
        String model = resolveModel(request.getModel(), resume.getModelId());
        long cost = generationCost();
        consume(user.getId(), cost, "Cover letter generation");

        try {
            String content = geminiResumeClient.generateCoverLetter(
                    resumeContext(resume), readAnalysis(resume), request.getJobDescription().trim(),
                    request.getCompany().trim(), request.getRole().trim(), request.getStyle(),
                    normalize(request.getInstructions()), model);
            CoverLetterProject project = CoverLetterProject.builder()
                    .userId(user.getId())
                    .resumeProjectId(resume.getId())
                    .company(request.getCompany().trim())
                    .role(request.getRole().trim())
                    .jobDescription(request.getJobDescription().trim())
                    .style(request.getStyle())
                    .modelId(model)
                    .lastInstructions(normalize(request.getInstructions()))
                    .content(validateGeneratedContent(content))
                    .build();
            projectRepository.save(project);
            return toResponse(project, resume);
        } catch (RuntimeException ex) {
            refund(user.getId(), cost);
            throw ex;
        }
    }

    @Override
    public CoverLetterResponse regenerate(String id, RegenerateCoverLetterRequest request) {
        User user = userService.getCurrentUser();
        enforceRateLimit(user.getId());
        CoverLetterProject project = ownedProject(id, user.getId());
        ResumeProject resume = ownedResume(project.getResumeProjectId(), user.getId());
        CoverLetterStyle style = request != null && request.getStyle() != null
                ? request.getStyle() : project.getStyle();
        String model = resolveModel(request == null ? null : request.getModel(), project.getModelId());
        String instructions = request == null ? "" : normalize(request.getInstructions());
        long cost = generationCost();
        consume(user.getId(), cost, "Cover letter regeneration");

        try {
            String content = geminiResumeClient.generateCoverLetter(
                    resumeContext(resume), readAnalysis(resume), project.getJobDescription(),
                    project.getCompany(), project.getRole(), style, instructions, model);
            project.setStyle(style);
            project.setModelId(model);
            project.setLastInstructions(instructions);
            project.setContent(validateGeneratedContent(content));
            projectRepository.save(project);
            return toResponse(project, resume);
        } catch (RuntimeException ex) {
            refund(user.getId(), cost);
            throw ex;
        }
    }

    @Override
    public CoverLetterResponse update(String id, UpdateCoverLetterRequest request) {
        User user = userService.getCurrentUser();
        CoverLetterProject project = ownedProject(id, user.getId());
        project.setContent(request.getContent().trim());
        projectRepository.save(project);
        return toResponse(project, ownedResume(project.getResumeProjectId(), user.getId()));
    }

    @Override
    public CoverLetterResponse get(String id) {
        User user = userService.getCurrentUser();
        CoverLetterProject project = ownedProject(id, user.getId());
        return toResponse(project, ownedResume(project.getResumeProjectId(), user.getId()));
    }

    @Override
    public List<CoverLetterSummaryResponse> getHistory() {
        String userId = userService.getCurrentUser().getId();
        return projectRepository.findTop30ByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public List<CoverLetterStyleResponse> getStyles() {
        return Arrays.stream(CoverLetterStyle.values())
                .map(style -> CoverLetterStyleResponse.builder()
                        .id(style.name())
                        .label(style.getLabel())
                        .description(style.getDescription())
                        .build())
                .toList();
    }

    @Override
    public DocumentDownload download(String id, String format) {
        User user = userService.getCurrentUser();
        CoverLetterProject project = ownedProject(id, user.getId());
        ResumeProject resume = ownedResume(project.getResumeProjectId(), user.getId());
        String normalized = format == null ? "pdf" : format.trim().toLowerCase();
        String base = fileBase(resume, project);
        return switch (normalized) {
            case "pdf" -> new DocumentDownload(documentService.createPdf(project, resume),
                    base + ".pdf", "application/pdf");
            case "docx" -> new DocumentDownload(documentService.createDocx(project, resume),
                    base + ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            default -> throw new CoverLetterException(HttpStatus.BAD_REQUEST,
                    "Download format must be pdf or docx.");
        };
    }

    @Override
    public void delete(String id) {
        User user = userService.getCurrentUser();
        projectRepository.delete(ownedProject(id, user.getId()));
    }

    private CoverLetterResponse toResponse(CoverLetterProject project, ResumeProject resume) {
        return CoverLetterResponse.builder()
                .id(project.getId())
                .resumeProjectId(resume.getId())
                .resumeFileName(resume.getOriginalFileName())
                .company(project.getCompany())
                .role(project.getRole())
                .jobDescription(project.getJobDescription())
                .style(project.getStyle().name())
                .styleLabel(project.getStyle().getLabel())
                .modelId(project.getModelId())
                .modelLabel(modelLabel(project.getModelId()))
                .content(project.getContent())
                .lastInstructions(project.getLastInstructions())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .pdfDownloadUrl("/api/cover-letters/" + project.getId() + "/download?format=pdf")
                .docxDownloadUrl("/api/cover-letters/" + project.getId() + "/download?format=docx")
                .build();
    }

    private CoverLetterSummaryResponse toSummary(CoverLetterProject project) {
        return CoverLetterSummaryResponse.builder()
                .id(project.getId())
                .company(project.getCompany())
                .role(project.getRole())
                .style(project.getStyle().name())
                .styleLabel(project.getStyle().getLabel())
                .modelLabel(modelLabel(project.getModelId()))
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private CoverLetterProject ownedProject(String id, String userId) {
        return projectRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new CoverLetterException(HttpStatus.NOT_FOUND,
                        "Cover letter not found."));
    }

    private ResumeProject ownedResume(String id, String userId) {
        return resumeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new CoverLetterException(HttpStatus.NOT_FOUND,
                        "Selected resume was not found. Analyze a resume first."));
    }

    private JsonNode readAnalysis(ResumeProject resume) {
        try {
            return objectMapper.readTree(resume.getAnalysisJson());
        } catch (Exception ex) {
            throw new CoverLetterException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The selected resume analysis could not be read.", ex);
        }
    }

    private String resumeContext(ResumeProject resume) {
        if (resume.getGeneratedResumeJson() != null && !resume.getGeneratedResumeJson().isBlank()) {
            return resume.getGeneratedResumeJson();
        }
        JsonNode normalized = readAnalysis(resume).path("normalizedResumeText");
        return normalized.asText("").isBlank() ? resume.getResumeText() : normalized.asText();
    }

    private String resolveModel(String requested, String stored) {
        String candidate = requested == null || requested.isBlank() ? stored : requested;
        String resolved = resumeProperties.resolveModel(candidate);
        if (resolved == null || resolved.isBlank()) {
            throw new CoverLetterException(HttpStatus.BAD_REQUEST,
                    "Selected Gemini model is not enabled for Cover Letter Studio.");
        }
        return resolved;
    }

    private String modelLabel(String id) {
        return resumeProperties.availableModels().stream()
                .filter(model -> model.getId().equals(id))
                .map(GeminiResumeProperties.ModelInfo::getLabel)
                .findFirst()
                .orElse(id);
    }

    private void enforceRateLimit(String userId) {
        RateLimitProperties.Limit limit = rateLimitProperties.getCoverLetter();
        long capacity = limit.getCapacity() > 0 ? limit.getCapacity() : 6;
        long refill = limit.getRefillTokens() > 0 ? limit.getRefillTokens() : 6;
        long minutes = limit.getRefillMinutes() > 0 ? limit.getRefillMinutes() : 1;
        RateLimitResponse response = redisRateLimitService.allowRequest(
                "cover-letter:" + userId, capacity, refill, minutes);
        if (!response.isAllowed()) throw new RateLImitException(response.getMessage());
    }

    private long generationCost() {
        return Math.max(0L, tokenProperties.getCoverLetter() == null
                ? 25L : tokenProperties.getCoverLetter());
    }

    private void consume(String userId, long cost, String description) {
        if (cost > 0) walletService.consumeTokens(userId, cost, FeatureType.RESUME, description);
    }

    private void refund(String userId, long cost) {
        if (cost <= 0) return;
        try {
            walletService.addTokens(userId, cost, FeatureType.RESUME,
                    "Refund failed cover letter generation");
        } catch (Exception refundError) {
            log.error("Cover letter refund failed for user {}", userId, refundError);
        }
    }

    private String validateGeneratedContent(String value) {
        String content = normalize(value);
        if (content.isBlank()) {
            throw new CoverLetterException(HttpStatus.BAD_GATEWAY,
                    "Cover Letter AI returned an empty response. Please retry.");
        }
        return content.length() <= 30_000 ? content : content.substring(0, 30_000);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String fileBase(ResumeProject resume, CoverLetterProject project) {
        String candidate = readAnalysis(resume).path("candidate").path("name").asText("Candidate");
        String value = candidate + "_" + project.getCompany() + "_Cover_Letter";
        value = value.replaceAll("[^A-Za-z0-9 -]", "").trim().replaceAll("\\s+", "_");
        return value.isBlank() ? "CareerForge_Cover_Letter" : value;
    }
}
