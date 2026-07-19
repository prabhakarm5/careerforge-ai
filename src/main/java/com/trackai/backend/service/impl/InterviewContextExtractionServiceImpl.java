package com.trackai.backend.service.impl;

import com.trackai.backend.config.GeminiResumeProperties;
import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.config.TokenProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.interview.InterviewContextExtractionResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.exception.InsufficientTokensException;
import com.trackai.backend.exception.InterviewException;
import com.trackai.backend.exception.RateLImitException;
import com.trackai.backend.service.InterviewContextExtractionService;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.ResumeDocumentTextExtractor;
import com.trackai.backend.service.UserService;
import com.trackai.backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewContextExtractionServiceImpl implements InterviewContextExtractionService {

    private static final int MAX_CONTEXT_CHARS = 20_000;

    private final ResumeDocumentTextExtractor documentTextExtractor;
    private final GeminiResumeClient geminiClient;
    private final GeminiResumeProperties geminiProperties;
    private final RedisRateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final TokenProperties tokenProperties;
    private final UserService userService;
    private final WalletService walletService;

    @Override
    public InterviewContextExtractionResponse extract(MultipartFile file, String requestedModel, String contextType) {
        User user = userService.getCurrentUser();
        RateLimitResponse limit = rateLimitService.allowRequest(
                "interview-context:" + user.getId(),
                rateLimitProperties.getInterview().getCapacity(),
                rateLimitProperties.getInterview().getRefillTokens(),
                rateLimitProperties.getInterview().getRefillMinutes());
        if (!limit.isAllowed()) throw new RateLImitException(limit.getMessage());

        boolean resumeContext = "RESUME".equalsIgnoreCase(contextType);
        ResumeDocumentTextExtractor.ExtractedResume document = documentTextExtractor.extract(file);
        String extractedText = normalize(document.text());
        boolean needsVision = document.inlineDocument() != null && document.inlineDocument().length > 0;
        long chargedTokens = 0L;

        if (needsVision) {
            String model = geminiProperties.resolveModel(requestedModel);
            if (model == null || model.isBlank()) {
                throw new InterviewException(HttpStatus.BAD_REQUEST, "Selected Gemini model is not enabled.");
            }
            chargedTokens = Math.max(0L, value(tokenProperties.getInterviewContext(), 5L));
            if (chargedTokens > 0) {
                if (walletService.getWalletByUserId(user.getId()).getRemainingTokens() < chargedTokens) {
                    String subject = resumeContext ? "scanned resume" : "scanned job description";
                    throw new InsufficientTokensException("Add credits to read this " + subject + ".");
                }
                walletService.consumeTokens(user.getId(), chargedTokens, FeatureType.INTERVIEW,
                        resumeContext ? "Interview resume extraction" : "Interview job description extraction");
            }
            try {
                extractedText = normalize(geminiClient.extractInterviewDocument(
                        extractedText, document.inlineDocument(), document.mimeType(), model,
                        resumeContext ? "RESUME" : "JOB_DESCRIPTION"));
            } catch (RuntimeException error) {
                refund(user.getId(), chargedTokens, resumeContext);
                throw error;
            }
        }

        if (extractedText.isBlank()) {
            throw new InterviewException(HttpStatus.UNPROCESSABLE_ENTITY,
                    resumeContext
                            ? "No readable resume text was found in this file."
                            : "No readable job-description text was found in this file.");
        }
        String limited = extractedText.substring(0, Math.min(MAX_CONTEXT_CHARS, extractedText.length()));
        return new InterviewContextExtractionResponse(
                document.fileName(), limited, needsVision ? "AI_VISION" : "DOCUMENT_TEXT", chargedTokens);
    }

    private void refund(String userId, long amount, boolean resumeContext) {
        if (amount <= 0) return;
        try {
            walletService.addTokens(userId, amount, FeatureType.INTERVIEW,
                    resumeContext ? "Refund failed resume extraction" : "Refund failed job description extraction");
        } catch (RuntimeException refundError) {
            log.error("Interview context extraction refund failed for user {}", userId, refundError);
        }
    }

    private long value(Long configured, long fallback) {
        return configured == null ? fallback : configured;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace("\u0000", "")
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}