package com.trackai.backend.service;

import com.trackai.backend.config.GeminiResumeProperties;
import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.config.TokenProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.entity.Wallet;
import com.trackai.backend.dto.interview.InterviewContextExtractionResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.service.impl.GeminiResumeClient;
import com.trackai.backend.service.impl.InterviewContextExtractionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InterviewContextExtractionServiceTest {

    private ResumeDocumentTextExtractor extractor;
    private GeminiResumeClient geminiClient;
    private GeminiResumeProperties geminiProperties;
    private RedisRateLimitService rateLimitService;
    private TokenProperties tokenProperties;
    private UserService userService;
    private WalletService walletService;
    private InterviewContextExtractionService service;

    @BeforeEach
    void setUp() {
        extractor = mock(ResumeDocumentTextExtractor.class);
        geminiClient = mock(GeminiResumeClient.class);
        geminiProperties = mock(GeminiResumeProperties.class);
        rateLimitService = mock(RedisRateLimitService.class);
        tokenProperties = new TokenProperties();
        userService = mock(UserService.class);
        walletService = mock(WalletService.class);
        RateLimitProperties limits = new RateLimitProperties();
        limits.getInterview().setCapacity(10);
        limits.getInterview().setRefillTokens(10);
        limits.getInterview().setRefillMinutes(1);
        when(userService.getCurrentUser()).thenReturn(User.builder().id("user-1").build());
        when(rateLimitService.allowRequest(eq("interview-context:user-1"), anyLong(), anyLong(), anyLong()))
                .thenReturn(RateLimitResponse.builder().allowed(true).build());
        service = new InterviewContextExtractionServiceImpl(
                extractor, geminiClient, geminiProperties, rateLimitService, limits,
                tokenProperties, userService, walletService);
    }

    @Test
    void textPdfUsesLocalExtractionWithoutAiCharge() {
        MockMultipartFile file = new MockMultipartFile("file", "job.pdf", "application/pdf", "pdf".getBytes());
        when(extractor.extract(file)).thenReturn(new ResumeDocumentTextExtractor.ExtractedResume(
                "job.pdf", "application/pdf", "Java backend engineer with Spring experience", null));

        InterviewContextExtractionResponse response = service.extract(file, "");

        assertThat(response.text()).contains("Spring experience");
        assertThat(response.sourceType()).isEqualTo("DOCUMENT_TEXT");
        assertThat(response.chargedTokens()).isZero();
        verifyNoInteractions(geminiClient);
        verify(walletService, never()).consumeTokens(eq("user-1"), anyLong(), eq(FeatureType.INTERVIEW), eq("Interview job description extraction"));
    }

    @Test
    void scannedImageUsesConfiguredVisionModelAndChargesConfiguredCost() {
        MockMultipartFile file = new MockMultipartFile("file", "job.png", "image/png", new byte[]{1, 2, 3});
        byte[] image = new byte[]{1, 2, 3};
        tokenProperties.setInterviewContext(7L);
        when(extractor.extract(file)).thenReturn(new ResumeDocumentTextExtractor.ExtractedResume(
                "job.png", "image/png", "", image));
        when(geminiProperties.resolveModel("gemini-test")).thenReturn("gemini-test");
        when(walletService.getWalletByUserId("user-1")).thenReturn(Wallet.builder().remainingTokens(100L).build());
        when(geminiClient.extractInterviewDocument("", image, "image/png", "gemini-test", "JOB_DESCRIPTION"))
                .thenReturn("Product manager responsibilities");

        InterviewContextExtractionResponse response = service.extract(file, "gemini-test");

        assertThat(response.text()).isEqualTo("Product manager responsibilities");
        assertThat(response.sourceType()).isEqualTo("AI_VISION");
        assertThat(response.chargedTokens()).isEqualTo(7L);
        verify(walletService).consumeTokens("user-1", 7L, FeatureType.INTERVIEW,
                "Interview job description extraction");
    }
}