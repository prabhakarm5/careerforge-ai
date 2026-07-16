package com.trackai.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.config.GeminiResumeProperties;
import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.config.TokenProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.interview.StartInterviewRequest;
import com.trackai.backend.entity.InterviewSession;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.enums.InterviewDifficulty;
import com.trackai.backend.enums.InterviewType;
import com.trackai.backend.repository.InterviewSessionRepository;
import com.trackai.backend.repository.InterviewTurnRepository;
import com.trackai.backend.repository.ResumeProjectRepository;
import com.trackai.backend.service.impl.GeminiResumeClient;
import com.trackai.backend.service.impl.InterviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InterviewServiceTest {
    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewTurnRepository turns = mock(InterviewTurnRepository.class);
    private final ResumeProjectRepository resumes = mock(ResumeProjectRepository.class);
    private final GeminiResumeClient gemini = mock(GeminiResumeClient.class);
    private final InterviewPersistenceService persistence = mock(InterviewPersistenceService.class);
    private final UserService users = mock(UserService.class);
    private final WalletService wallet = mock(WalletService.class);
    private final RedisRateLimitService rateLimits = mock(RedisRateLimitService.class);
    private final RateLimitProperties rateProperties = new RateLimitProperties();
    private final TokenProperties tokenProperties = new TokenProperties();
    private final GeminiResumeProperties geminiProperties = new GeminiResumeProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private InterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        tokenProperties.setInterviewStart(12L);
        geminiProperties.setModel("gemini-test");
        service = new InterviewServiceImpl(sessions, turns, resumes, gemini, persistence, users,
                wallet, rateLimits, rateProperties, tokenProperties, geminiProperties, objectMapper);
        when(users.getCurrentUser()).thenReturn(User.builder().id("user-1").name("User")
                .email("user@example.com").password("unused").mobileNumber("9999999999").build());
        when(rateLimits.allowRequest(anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(RateLimitResponse.builder().allowed(true).build());
        doAnswer(invocation -> {
            InterviewSession session = invocation.getArgument(0);
            session.setId("interview-1");
            return null;
        }).when(persistence).saveSession(any());
    }

    @Test
    void startsAdaptiveInterviewAndChargesConfiguredCredits() throws Exception {
        when(gemini.generateInterviewQuestion(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), eq(1), eq("gemini-test")))
                .thenReturn(objectMapper.readTree("{\"question\":\"Explain a service you designed.\",\"focus\":\"Backend design\"}"));

        var response = service.start(request());

        assertThat(response.getId()).isEqualTo("interview-1");
        assertThat(response.getCurrentQuestion()).contains("service you designed");
        verify(wallet).consumeTokens("user-1", 12L, FeatureType.INTERVIEW, "Interview practice start");
        verify(persistence).saveSession(any(InterviewSession.class));
    }

    @Test
    void refundsCreditsWhenQuestionProviderFails() {
        when(gemini.generateInterviewQuestion(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("Provider unavailable"));

        assertThatThrownBy(() -> service.start(request())).hasMessage("Provider unavailable");
        verify(wallet).addTokens("user-1", 12L, FeatureType.INTERVIEW, "Refund failed interview start");
        verify(persistence, never()).saveSession(any());
    }

    private StartInterviewRequest request() {
        StartInterviewRequest request = new StartInterviewRequest();
        request.setRole("Java Backend Developer");
        request.setCompany("CareerForge");
        request.setJobDescription("Build secure Spring Boot services and PostgreSQL APIs.");
        request.setType(InterviewType.MIXED);
        request.setDifficulty(InterviewDifficulty.INTERMEDIATE);
        request.setQuestionCount(5);
        request.setModel("gemini-test");
        return request;
    }
}
