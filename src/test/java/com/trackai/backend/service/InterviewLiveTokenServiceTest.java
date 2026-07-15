package com.trackai.backend.service;

import com.trackai.backend.config.GeminiLiveProperties;
import com.trackai.backend.config.GeminiResumeProperties;
import com.trackai.backend.config.TokenProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.interview.LiveInterviewTokenRequest;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.exception.InterviewException;
import com.trackai.backend.security.JwtUserPrincipal;
import com.trackai.backend.service.impl.InterviewLiveTokenServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class InterviewLiveTokenServiceTest {
    private DisposableServer server;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (server != null) server.disposeNow();
    }

    @Test
    void chargesConfiguredCreditsWhenTokenIsIssued() {
        startProvider(200, "{\"name\":\"ephemeral-token\"}");
        WalletService wallet = mock(WalletService.class);
        InterviewLiveTokenServiceImpl service = service(wallet);

        var response = service.create(request());

        assertThat(response.getToken()).isEqualTo("ephemeral-token");
        assertThat(response.getChargedTokens()).isEqualTo(25L);
        verify(wallet).consumeTokens("user-1", 25L, FeatureType.INTERVIEW, "Live AI interview room");
        verify(wallet, never()).addTokens(anyString(), anyLong(), any(), anyString());
    }

    @Test
    void refundsCreditsWhenProviderRejectsTokenRequest() {
        startProvider(503, "{\"error\":{\"message\":\"unavailable\"}}");
        WalletService wallet = mock(WalletService.class);
        InterviewLiveTokenServiceImpl service = service(wallet);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(InterviewException.class)
                .hasMessageContaining("could not connect quickly enough");

        verify(wallet).consumeTokens("user-1", 25L, FeatureType.INTERVIEW, "Live AI interview room");
        verify(wallet).addTokens("user-1", 25L, FeatureType.INTERVIEW,
                "Refund failed live interview connection");
    }

    private InterviewLiveTokenServiceImpl service(WalletService wallet) {
        GeminiResumeProperties gemini = new GeminiResumeProperties();
        gemini.setApiKey("test-key");
        gemini.setBaseUrl("http://127.0.0.1:" + server.port());
        GeminiLiveProperties live = new GeminiLiveProperties();
        live.setEnabled(true);
        live.setModel("gemini-live-test");
        live.setVoice("Kore");
        TokenProperties tokens = new TokenProperties();
        tokens.setInterviewLive(25L);
        RedisRateLimitService rateLimits = mock(RedisRateLimitService.class);
        when(rateLimits.allowRequest(anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(RateLimitResponse.builder().allowed(true).build());
        UserService users = mock(UserService.class);
        JwtUserPrincipal principal = new JwtUserPrincipal("user-1", "user@example.com", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return new InterviewLiveTokenServiceImpl(gemini, live, rateLimits, users, wallet, tokens,
                WebClient.builder().baseUrl(gemini.getBaseUrl()).build());
    }

    private void startProvider(int status, String body) {
        server = HttpServer.create().host("127.0.0.1").port(0)
                .route(routes -> routes.post("/v1alpha/auth_tokens", (request, response) ->
                        response.status(status)
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .sendString(Mono.just(body))))
                .bindNow();
    }

    private LiveInterviewTokenRequest request() {
        LiveInterviewTokenRequest request = new LiveInterviewTokenRequest();
        request.setRole("Java Backend Developer");
        request.setCompany("CareerForge");
        request.setJobDescription("Build secure Spring Boot APIs and distributed systems.");
        request.setLanguage("AUTO");
        request.setInterviewerStyle("BALANCED");
        request.setInterviewType("MIXED");
        request.setDifficulty("INTERMEDIATE");
        return request;
    }
}
