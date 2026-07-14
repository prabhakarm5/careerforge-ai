package com.trackai.backend.service.impl;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.admin.AdminSystemStatusResponse;
import com.trackai.backend.service.AdminSystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminSystemServiceImpl implements AdminSystemService {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final Environment environment;
    private final RateLimitProperties rateLimitProperties;

    @Override
    public AdminSystemStatusResponse getStatus() {
        CompletableFuture<AdminSystemStatusResponse.ComponentStatus> database =
                CompletableFuture.supplyAsync(this::databaseStatus);
        CompletableFuture<AdminSystemStatusResponse.ComponentStatus> redis =
                CompletableFuture.supplyAsync(this::redisStatus);

        Map<String, AdminSystemStatusResponse.ComponentStatus> services = new LinkedHashMap<>();
        services.put("postgresql", database.join());
        services.put("redis", redis.join());
        services.put("email", configuredStatus(
                configured("spring.mail.host") && configured("spring.mail.username"),
                "SMTP configuration"));

        Map<String, Boolean> integrations = new LinkedHashMap<>();
        integrations.put("groq", configured("groq.api-key"));
        integrations.put("openRouter", configured("openrouter.api-key"));
        integrations.put("geminiResume", configured("gemini.resume.api-key"));
        integrations.put("jobSearch", configured("jobs.provider.app-id"));
        integrations.put("cloudinary", configured("cloudinary.api-key"));
        integrations.put("razorpay", configured("razorpay.key-id"));
        integrations.put("googleOAuth", configured("spring.security.oauth2.client.registration.google.client-id"));
        integrations.put("githubOAuth", configured("spring.security.oauth2.client.registration.github.client-id"));

        boolean coreUp = services.get("postgresql").status().equals("UP")
                && services.get("redis").status().equals("UP");
        return new AdminSystemStatusResponse(
                Instant.now(),
                coreUp ? "OPERATIONAL" : "DEGRADED",
                services,
                integrations,
                rateLimits());
    }

    private AdminSystemStatusResponse.ComponentStatus databaseStatus() {
        long started = System.nanoTime();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
            statement.setQueryTimeout(3);
            statement.execute();
            return component("UP", started, "Database query succeeded");
        } catch (Exception exception) {
            return component("DOWN", started, safeMessage(exception));
        }
    }

    private AdminSystemStatusResponse.ComponentStatus redisStatus() {
        long started = System.nanoTime();
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            return component("PONG".equalsIgnoreCase(pong) ? "UP" : "DEGRADED", started,
                    pong == null ? "No ping response" : "Redis responded");
        } catch (Exception exception) {
            return component("DOWN", started, safeMessage(exception));
        }
    }

    private AdminSystemStatusResponse.ComponentStatus configuredStatus(boolean configured, String label) {
        return new AdminSystemStatusResponse.ComponentStatus(
                configured ? "CONFIGURED" : "NOT_CONFIGURED",
                0,
                label + (configured ? " is present" : " is missing"));
    }

    private AdminSystemStatusResponse.ComponentStatus component(String status, long started, String message) {
        long latencyMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        return new AdminSystemStatusResponse.ComponentStatus(status, latencyMs, message);
    }

    private Map<String, AdminSystemStatusResponse.RateLimitStatus> rateLimits() {
        Map<String, AdminSystemStatusResponse.RateLimitStatus> values = new LinkedHashMap<>();
        add(values, "login", rateLimitProperties.getLogin());
        add(values, "refreshToken", rateLimitProperties.getRefreshToken());
        add(values, "chat", rateLimitProperties.getChat());
        add(values, "resume", rateLimitProperties.getResume());
        add(values, "jobSearch", rateLimitProperties.getJobSearch());
        add(values, "image", rateLimitProperties.getImage());
        add(values, "createOrder", rateLimitProperties.getCreateOrder());
        add(values, "verifyPayment", rateLimitProperties.getVerifyPayment());
        add(values, "promoClaim", rateLimitProperties.getPromoClaim());
        add(values, "support", rateLimitProperties.getSupport());
        return values;
    }

    private void add(
            Map<String, AdminSystemStatusResponse.RateLimitStatus> values,
            String name,
            RateLimitProperties.Limit limit) {
        values.put(name, new AdminSystemStatusResponse.RateLimitStatus(
                limit.getCapacity(), limit.getRefillTokens(), limit.getRefillMinutes()));
    }

    private boolean configured(String property) {
        String value = environment.getProperty(property);
        return value != null
                && !value.isBlank()
                && !value.startsWith("CHANGE_ME")
                && !value.equalsIgnoreCase("null");
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "Health check failed";
        return message.length() <= 160 ? message : message.substring(0, 160);
    }
}