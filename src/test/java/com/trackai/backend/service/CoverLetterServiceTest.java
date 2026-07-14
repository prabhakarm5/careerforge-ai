package com.trackai.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.config.GeminiResumeProperties;
import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.config.TokenProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.coverletter.GenerateCoverLetterRequest;
import com.trackai.backend.entity.CoverLetterProject;
import com.trackai.backend.entity.ResumeProject;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.CoverLetterStyle;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.exception.ResumeProcessingException;
import com.trackai.backend.repository.CoverLetterProjectRepository;
import com.trackai.backend.repository.ResumeProjectRepository;
import com.trackai.backend.service.impl.CoverLetterServiceImpl;
import com.trackai.backend.service.impl.GeminiResumeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoverLetterServiceTest {

    private final CoverLetterProjectRepository projects = mock(CoverLetterProjectRepository.class);
    private final ResumeProjectRepository resumes = mock(ResumeProjectRepository.class);
    private final GeminiResumeClient gemini = mock(GeminiResumeClient.class);
    private final CoverLetterDocumentService documents = mock(CoverLetterDocumentService.class);
    private final UserService users = mock(UserService.class);
    private final WalletService wallet = mock(WalletService.class);
    private final RedisRateLimitService rateLimits = mock(RedisRateLimitService.class);
    private final RateLimitProperties rateProperties = new RateLimitProperties();
    private final TokenProperties tokenProperties = new TokenProperties();
    private final GeminiResumeProperties geminiProperties = new GeminiResumeProperties();
    private CoverLetterServiceImpl service;

    @BeforeEach
    void setUp() {
        tokenProperties.setCoverLetter(25L);
        geminiProperties.setModel("gemini-test");
        service = new CoverLetterServiceImpl(projects, resumes, gemini, documents, users, wallet,
                rateLimits, rateProperties, tokenProperties, geminiProperties, new ObjectMapper());

        User user = User.builder().id("user-1").name("User").email("user@example.com")
                .password("unused").mobileNumber("9999999999").build();
        ResumeProject resume = ResumeProject.builder()
                .id("resume-1").userId("user-1").originalFileName("resume.pdf")
                .resumeText("Java backend engineer with Spring Boot experience.")
                .analysisJson("{\"candidate\":{\"name\":\"Test User\"},\"normalizedResumeText\":\"Java backend engineer.\"}")
                .modelId("gemini-test").build();
        when(users.getCurrentUser()).thenReturn(user);
        when(resumes.findByIdAndUserId("resume-1", "user-1")).thenReturn(Optional.of(resume));
        when(rateLimits.allowRequest(anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(RateLimitResponse.builder().allowed(true).build());
        when(projects.save(any(CoverLetterProject.class))).thenAnswer(invocation -> {
            CoverLetterProject project = invocation.getArgument(0);
            project.setId("letter-1");
            return project;
        });
    }

    @Test
    void generatesUserOwnedLetterAndChargesConfiguredCredits() {
        when(gemini.generateCoverLetter(anyString(), any(), anyString(), anyString(), anyString(),
                eq(CoverLetterStyle.PROFESSIONAL), anyString(), eq("gemini-test")))
                .thenReturn("Dear Hiring Manager,\n\nI am ready to contribute.\n\nSincerely,\nTest User");

        var response = service.generate(request());

        assertThat(response.getId()).isEqualTo("letter-1");
        assertThat(response.getCompany()).isEqualTo("CareerForge");
        assertThat(response.getContent()).contains("Dear Hiring Manager");
        verify(wallet).consumeTokens("user-1", 25L, FeatureType.RESUME,
                "Cover letter generation");
        verify(wallet, never()).addTokens(anyString(), anyLong(), any(), anyString());
    }

    @Test
    void refundsCreditsWhenProviderFails() {
        when(gemini.generateCoverLetter(anyString(), any(), anyString(), anyString(), anyString(),
                any(), anyString(), anyString()))
                .thenThrow(new ResumeProcessingException(HttpStatus.BAD_GATEWAY, "Provider unavailable"));

        assertThatThrownBy(() -> service.generate(request()))
                .isInstanceOf(ResumeProcessingException.class)
                .hasMessage("Provider unavailable");
        verify(wallet).addTokens("user-1", 25L, FeatureType.RESUME,
                "Refund failed cover letter generation");
        verify(projects, never()).save(any());
    }

    private GenerateCoverLetterRequest request() {
        GenerateCoverLetterRequest request = new GenerateCoverLetterRequest();
        request.setResumeProjectId("resume-1");
        request.setCompany("CareerForge");
        request.setRole("Backend Engineer");
        request.setJobDescription("Build secure Spring Boot services and PostgreSQL APIs.");
        request.setStyle(CoverLetterStyle.PROFESSIONAL);
        request.setModel("gemini-test");
        return request;
    }
}
