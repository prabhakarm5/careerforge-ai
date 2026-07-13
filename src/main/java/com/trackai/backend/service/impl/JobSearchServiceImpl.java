package com.trackai.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackai.backend.config.JobSearchProperties;
import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.job.JobListingResponse;
import com.trackai.backend.dto.job.JobSearchResponse;
import com.trackai.backend.exception.JobSearchException;
import com.trackai.backend.service.JobSearchService;
import com.trackai.backend.service.RedisRateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class JobSearchServiceImpl implements JobSearchService {

    private final JobSearchProperties properties;
    private final WebClient webClient;
    private final RedisRateLimitService rateLimitService;
    private final RateLimitProperties rateLimits;
    private final Map<String, CachedSearch> cache = new ConcurrentHashMap<>();

    public JobSearchServiceImpl(
            JobSearchProperties properties,
            WebClient.Builder webClientBuilder,
            RedisRateLimitService rateLimitService,
            RateLimitProperties rateLimits) {
        this.properties = properties;
        this.webClient = webClientBuilder.clone().baseUrl(properties.getBaseUrl()).build();
        this.rateLimitService = rateLimitService;
        this.rateLimits = rateLimits;
    }

    public JobSearchResponse search(String query, String location, String country, int page) {
        if (!properties.configured()) {
            throw new JobSearchException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Job discovery is not configured yet. Add ADZUNA_APP_ID and ADZUNA_APP_KEY to the backend environment.");
        }

        String safeQuery = required(query, "Job title or skills", 120);
        String safeLocation = optional(location, 120);
        String countryValue = optional(country, 2).toLowerCase(Locale.ROOT);
        if (countryValue.isBlank()) countryValue = properties.getDefaultCountry().toLowerCase(Locale.ROOT);
        final String safeCountry = countryValue;
        if (!safeCountry.matches("[a-z]{2}")) {
            throw new JobSearchException(HttpStatus.BAD_REQUEST, "Country must be a two-letter code such as in, gb, or us.");
        }
        int safePage = Math.max(1, Math.min(page, 20));
        int pageSize = Math.max(5, Math.min(properties.getResultsPerPage(), 50));
        int maxAgeDays = Math.max(1, Math.min(properties.getMaxAgeDays(), 90));
        String cacheKey = String.join("|", safeQuery.toLowerCase(Locale.ROOT),
                safeLocation.toLowerCase(Locale.ROOT), safeCountry, String.valueOf(safePage));
        CachedSearch cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.response();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String identity = authentication == null ? "anonymous" : authentication.getName();
        RateLimitProperties.Limit limit = rateLimits.getJobSearch();
        RateLimitResponse allowance = rateLimitService.allowRequest(
                "job-search:" + Integer.toHexString(identity.toLowerCase(Locale.ROOT).hashCode()),
                limit.getCapacity(), limit.getRefillTokens(), limit.getRefillMinutes());
        if (!allowance.isAllowed()) {
            throw new JobSearchException(HttpStatus.TOO_MANY_REQUESTS, allowance.getMessage());
        }

        try {
            JsonNode payload = webClient.get()
                    .uri(builder -> {
                        var uri = builder.path("/v1/api/jobs/{country}/search/{page}")
                                .queryParam("app_id", properties.getAppId())
                                .queryParam("app_key", properties.getAppKey())
                                .queryParam("results_per_page", pageSize)
                                .queryParam("what", safeQuery)
                                .queryParam("sort_by", "date")
                                .queryParam("max_days_old", maxAgeDays)
                                .queryParam("content-type", "application/json");
                        if (!safeLocation.isBlank()) uri.queryParam("where", safeLocation);
                        return uri.build(safeCountry, safePage);
                    })
                    .retrieve()
                    .onStatus(status -> status.value() == 401 || status.value() == 403,
                            ignored -> reactor.core.publisher.Mono.error(new JobSearchException(
                                    HttpStatus.SERVICE_UNAVAILABLE, "Job provider credentials are invalid.")))
                    .onStatus(status -> status.value() == 429,
                            ignored -> reactor.core.publisher.Mono.error(new JobSearchException(
                                    HttpStatus.TOO_MANY_REQUESTS, "Job search is busy. Please retry in a moment.")))
                    .onStatus(status -> status.isError(),
                            ignored -> reactor.core.publisher.Mono.error(new JobSearchException(
                                    HttpStatus.BAD_GATEWAY, "The job provider could not complete this search.")))
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds())))
                    .block();

            List<JobListingResponse> jobs = mapJobs(payload, maxAgeDays);
            JobSearchResponse response = JobSearchResponse.builder()
                    .query(safeQuery)
                    .location(safeLocation)
                    .country(safeCountry)
                    .page(safePage)
                    .totalResults(payload == null ? 0 : payload.path("count").asLong(jobs.size()))
                    .attribution("Jobs by Adzuna")
                    .jobs(jobs)
                    .build();
            if (cache.size() > 200) cache.clear();
            cache.put(cacheKey, new CachedSearch(Instant.now().plus(Duration.ofMinutes(2)), response));
            log.info("Job search completed: country={}, page={}, results={}", safeCountry, safePage, jobs.size());
            return response;
        } catch (JobSearchException ex) {
            log.warn("Job provider rejected search for country {}: {}", safeCountry, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("Job provider request failed for country {}: {}", safeCountry, ex.getMessage());
            throw new JobSearchException(HttpStatus.BAD_GATEWAY,
                    "Live jobs could not be loaded right now. Please retry.", ex);
        }
    }

    private List<JobListingResponse> mapJobs(JsonNode payload, int maxAgeDays) {
        List<JobListingResponse> jobs = new ArrayList<>();
        if (payload == null || !payload.path("results").isArray()) return jobs;
        Instant cutoff = Instant.now().minus(Duration.ofDays(maxAgeDays + 1L));
        for (JsonNode item : payload.path("results")) {
            Instant postedAt = instant(item.path("created").asText(""));
            if (postedAt != null && postedAt.isBefore(cutoff)) continue;
            String applyUrl = safeUrl(item.path("redirect_url").asText(""));
            if (applyUrl.isBlank()) continue;
            jobs.add(JobListingResponse.builder()
                    .id(item.path("id").asText(""))
                    .title(text(item, "title", "Untitled role"))
                    .company(text(item.path("company"), "display_name", "Company not listed"))
                    .location(text(item.path("location"), "display_name", "Location not listed"))
                    .description(cleanDescription(item.path("description").asText("")))
                    .category(text(item.path("category"), "label", ""))
                    .contractType(text(item, "contract_type", ""))
                    .contractTime(text(item, "contract_time", ""))
                    .salaryMin(decimal(item.path("salary_min")))
                    .salaryMax(decimal(item.path("salary_max")))
                    .postedAt(postedAt)
                    .applyUrl(applyUrl)
                    .source("Adzuna")
                    .build());
        }
        return jobs;
    }

    private String cleanDescription(String value) {
        String cleaned = value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 600 ? cleaned : cleaned.substring(0, 597) + "...";
    }

    private String safeUrl(String value) {
        String url = value.trim();
        return url.startsWith("https://") || url.startsWith("http://") ? url : "";
    }

    private Instant instant(String value) {
        try {
            return value.isBlank() ? null : Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private BigDecimal decimal(JsonNode value) {
        return value.isNumber() ? value.decimalValue() : null;
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? fallback : value;
    }

    private String required(String value, String label, int max) {
        String normalized = optional(value, max);
        if (normalized.isBlank()) throw new JobSearchException(HttpStatus.BAD_REQUEST, label + " is required.");
        return normalized;
    }

    private String optional(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > max) {
            throw new JobSearchException(HttpStatus.BAD_REQUEST, "Search value is too long.");
        }
        return normalized;
    }
    private record CachedSearch(Instant expiresAt, JobSearchResponse response) {}
}