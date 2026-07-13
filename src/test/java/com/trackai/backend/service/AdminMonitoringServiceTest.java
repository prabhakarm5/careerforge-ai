package com.trackai.backend.service;

import com.trackai.backend.config.MonitoringProperties;
import com.trackai.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminMonitoringServiceTest {

    private UserRepository userRepository;
    private MonitoringProperties properties;
    private AdminMonitoringService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        properties = new MonitoringProperties();
        properties.setMaxEvents(100);
        properties.setRetentionHours(24);
        service = new AdminMonitoringService(userRepository, properties);
    }

    @Test
    void masksIdentityAndNormalizesDynamicRoutes() {
        properties.setTrustProxyHeaders(true);
        HttpServletRequest request = request(
                "/api/conversations/550e8400-e29b-41d4-a716-446655440000",
                "203.0.113.42",
                "IN",
                "administrator@example.com");

        service.recordRequest(request, 200, 18);

        var overview = service.overview();
        var entry = overview.recentRequests().getFirst();
        assertThat(entry.path()).isEqualTo("/api/conversations/{id}");
        assertThat(entry.maskedIp()).isEqualTo("203.0.113.x");
        assertThat(entry.user()).isEqualTo("ad***@example.com");
        assertThat(entry.country()).isEqualTo("IN");
    }

    @Test
    void ignoresForwardedIdentityWhenProxyHeadersAreNotTrusted() {
        properties.setTrustProxyHeaders(false);
        HttpServletRequest request = request(
                "/api/chat",
                "10.0.0.8",
                "US",
                null);

        service.recordRequest(request, 503, 90);

        var overview = service.overview();
        var entry = overview.recentRequests().getFirst();
        assertThat(entry.maskedIp()).isEqualTo("10.0.0.x");
        assertThat(entry.country()).isEqualTo("Unknown");
        assertThat(entry.user()).isEqualTo("Anonymous");
        assertThat(overview.statusCodes()).containsEntry("5xx", 1L);
    }

    private HttpServletRequest request(
            String path,
            String remoteAddress,
            String country,
            String username) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(path);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn(remoteAddress);
        when(request.getHeader("X-Forwarded-For")).thenReturn(remoteAddress);
        when(request.getHeader("CF-IPCountry")).thenReturn(country);
        Principal principal = username == null ? null : () -> username;
        when(request.getUserPrincipal()).thenReturn(principal);
        return request;
    }
}
