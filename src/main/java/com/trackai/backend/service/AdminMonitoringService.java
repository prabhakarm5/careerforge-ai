package com.trackai.backend.service;

import com.sun.management.OperatingSystemMXBean;
import com.trackai.backend.config.MonitoringProperties;
import com.trackai.backend.dto.admin.AdminMonitoringResponse;
import com.trackai.backend.dto.admin.AdminUserActivityResponse;
import com.trackai.backend.dto.admin.AdminUserResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminMonitoringService {

    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> COUNTRY_HEADERS = Set.of(
            "CF-IPCountry", "CloudFront-Viewer-Country", "X-Country-Code");
    private static final String UNKNOWN = "Unknown";

    private final UserRepository userRepository;
    private final MonitoringProperties properties;
    private final ConcurrentLinkedDeque<RequestSnapshot> requests = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<PageSnapshot> pageViews = new ConcurrentLinkedDeque<>();
    private final AtomicInteger requestSize = new AtomicInteger();
    private final AtomicInteger pageViewSize = new AtomicInteger();
    private volatile UserMetricsCache userMetricsCache;

    public void recordRequest(HttpServletRequest request, int status, long durationMs) {
        String identity = request.getUserPrincipal() == null
                ? "Anonymous"
                : clean(request.getUserPrincipal().getName(), 180, UNKNOWN);
        String country = country(request);
        String timezone = firstNonBlank(
                request.getHeader("X-Client-Timezone"), request.getHeader("X-Timezone"));
        String locale = firstNonBlank(request.getHeader("X-Client-Locale"), request.getLocale() == null
                ? null : request.getLocale().toLanguageTag());
        String ip = clientIp(request);

        addBounded(requests, requestSize, new RequestSnapshot(
                Instant.now(),
                request.getMethod(),
                normalizePath(request.getRequestURI()),
                status,
                Math.max(0, durationMs),
                ip,
                country,
                location(country, timezone, locale),
                identity,
                clean(request.getHeader("User-Agent"), 180, UNKNOWN)));
    }

    public void recordPageView(
            String rawPath,
            String timezone,
            String locale,
            HttpServletRequest request) {
        String identity = request.getUserPrincipal() == null
                ? "Anonymous"
                : clean(request.getUserPrincipal().getName(), 180, UNKNOWN);
        String country = country(request);
        addBounded(pageViews, pageViewSize, new PageSnapshot(
                Instant.now(), normalizePagePath(rawPath), identity,
                country, location(country, timezone, locale)));
    }

    public AdminMonitoringResponse overview() {
        Instant now = Instant.now();
        Instant cutoff = retentionCutoff(now);
        List<RequestSnapshot> recent = recentRequests(cutoff);
        List<PageSnapshot> pages = pageViews.stream()
                .filter(item -> !item.timestamp().isBefore(cutoff))
                .toList();

        long errors = recent.stream().filter(item -> item.status() >= 400).count();
        double averageLatency = recent.stream().mapToLong(RequestSnapshot::durationMs).average().orElse(0);
        long activeUsers = recent.stream()
                .map(RequestSnapshot::userIdentity)
                .filter(user -> !"Anonymous".equals(user))
                .distinct()
                .count();

        AdminMonitoringResponse.TrafficMetrics traffic = new AdminMonitoringResponse.TrafficMetrics(
                recent.size(), errors,
                recent.isEmpty() ? 0 : round(errors * 100.0 / recent.size()),
                round(averageLatency), pages.size(),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, activeUsers)));

        return new AdminMonitoringResponse(
                now,
                systemMetrics(),
                userMetrics(now),
                traffic,
                requestTimeline(recent, now),
                countRequests(recent, 8),
                countPages(pages, 8),
                countryMetrics(recent, pages),
                endpointPerformance(recent, 8),
                statusCodes(recent),
                recent.stream().limit(100).map(this::toEntry).toList());
    }

    public AdminUserActivityResponse activityForUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Instant cutoff = retentionCutoff(Instant.now());
        List<RequestSnapshot> activity = recentRequests(cutoff).stream()
                .filter(item -> user.getEmail().equalsIgnoreCase(item.userIdentity()))
                .toList();
        long errors = activity.stream().filter(item -> item.status() >= 400).count();
        double average = activity.stream().mapToLong(RequestSnapshot::durationMs).average().orElse(0);
        Instant lastSeen = activity.isEmpty() ? null : activity.getFirst().timestamp();

        return new AdminUserActivityResponse(
                AdminUserResponse.from(user), activity.size(), errors, round(average), lastSeen,
                activity.stream().limit(150).map(this::toEntry).toList());
    }

    private AdminMonitoringResponse.SystemMetrics systemMetrics() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        double processCpu = 0;
        double systemCpu = 0;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os) {
            processCpu = percent(os.getProcessCpuLoad());
            systemCpu = percent(os.getCpuLoad());
        }
        return new AdminMonitoringResponse.SystemMetrics(
                processCpu, systemCpu,
                memory.getHeapMemoryUsage().getUsed(), memory.getHeapMemoryUsage().getMax(),
                threads.getThreadCount(), ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
    }

    private AdminMonitoringResponse.UserMetrics userMetrics(Instant now) {
        UserMetricsCache cached = userMetricsCache;
        if (cached != null && now.isBefore(cached.expiresAt())) return cached.metrics();

        var counts = userRepository.getAdminUserMetrics(LocalDate.now().minusDays(6).atStartOfDay());
        AdminMonitoringResponse.UserMetrics metrics = counts == null
                ? new AdminMonitoringResponse.UserMetrics(0, 0, 0, 0, 0)
                : new AdminMonitoringResponse.UserMetrics(
                        number(counts.getTotal()), number(counts.getEnabledUsers()),
                        number(counts.getBlockedUsers()), number(counts.getVerifiedUsers()),
                        number(counts.getRecentUsers()));
        userMetricsCache = new UserMetricsCache(now.plusSeconds(30), metrics);
        return metrics;
    }

    private List<AdminMonitoringResponse.MetricPoint> requestTimeline(
            List<RequestSnapshot> recent, Instant now) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime end = now.atZone(zone).withMinute(0).withSecond(0).withNano(0);
        List<AdminMonitoringResponse.MetricPoint> points = new ArrayList<>();
        for (int offset = 11; offset >= 0; offset--) {
            ZonedDateTime bucketStart = end.minusHours(offset);
            Instant from = bucketStart.toInstant();
            Instant to = bucketStart.plusHours(1).toInstant();
            long value = recent.stream()
                    .filter(item -> !item.timestamp().isBefore(from) && item.timestamp().isBefore(to))
                    .count();
            points.add(new AdminMonitoringResponse.MetricPoint(bucketStart.format(HOUR_LABEL), value));
        }
        return points;
    }

    private List<AdminMonitoringResponse.NamedMetric> countRequests(List<RequestSnapshot> values, int limit) {
        return top(values.stream().collect(Collectors.groupingBy(RequestSnapshot::path, Collectors.counting())), limit);
    }

    private List<AdminMonitoringResponse.NamedMetric> countPages(List<PageSnapshot> values, int limit) {
        return top(values.stream().collect(Collectors.groupingBy(PageSnapshot::path, Collectors.counting())), limit);
    }

    private List<AdminMonitoringResponse.NamedMetric> countryMetrics(
            List<RequestSnapshot> recent, List<PageSnapshot> pages) {
        Map<String, Long> values = recent.stream().collect(Collectors.groupingBy(RequestSnapshot::location, Collectors.counting()));
        pages.forEach(page -> values.merge(page.location(), 1L, Long::sum));
        return top(values, 8);
    }

    private List<AdminMonitoringResponse.EndpointPerformance> endpointPerformance(
            List<RequestSnapshot> recent, int limit) {
        return recent.stream().collect(Collectors.groupingBy(RequestSnapshot::path))
                .entrySet().stream()
                .map(entry -> {
                    List<Long> times = entry.getValue().stream()
                            .map(RequestSnapshot::durationMs).sorted().toList();
                    long p95 = percentile(times, 0.95);
                    long max = times.isEmpty() ? 0 : times.getLast();
                    long errors = entry.getValue().stream().filter(item -> item.status() >= 400).count();
                    double average = entry.getValue().stream().mapToLong(RequestSnapshot::durationMs).average().orElse(0);
                    return new AdminMonitoringResponse.EndpointPerformance(
                            entry.getKey(), entry.getValue().size(), round(average), p95, max, errors);
                })
                .sorted(Comparator.comparingLong(AdminMonitoringResponse.EndpointPerformance::p95LatencyMs).reversed()
                        .thenComparing(AdminMonitoringResponse.EndpointPerformance::path))
                .limit(limit)
                .toList();
    }

    private long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        int index = Math.max(0, (int) Math.ceil(values.size() * percentile) - 1);
        return values.get(Math.min(index, values.size() - 1));
    }

    private List<AdminMonitoringResponse.NamedMetric> top(Map<String, Long> values, int limit) {
        return values.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> new AdminMonitoringResponse.NamedMetric(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Map<String, Long> statusCodes(List<RequestSnapshot> recent) {
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("2xx", 0L);
        values.put("3xx", 0L);
        values.put("4xx", 0L);
        values.put("5xx", 0L);
        recent.forEach(item -> {
            String key = (item.status() / 100) + "xx";
            if (values.containsKey(key)) values.computeIfPresent(key, (ignored, count) -> count + 1);
        });
        return values;
    }

    private AdminMonitoringResponse.RequestEntry toEntry(RequestSnapshot item) {
        return new AdminMonitoringResponse.RequestEntry(
                item.timestamp(), item.method(), item.path(), item.status(), item.durationMs(),
                maskIp(item.clientIp()), item.country(), safeUser(item.userIdentity()),
                item.clientIp(), item.location(), item.userAgent());
    }

    private String clientIp(HttpServletRequest request) {
        String value = request.getRemoteAddr();
        if (properties.isTrustProxyHeaders()) {
            String forwarded = firstNonBlank(request.getHeader("X-Forwarded-For"), request.getHeader("X-Real-IP"));
            if (forwarded != null) value = forwarded.split(",", 2)[0].trim();
        }
        return clean(value, 64, UNKNOWN);
    }

    private String maskIp(String value) {
        if (value == null || value.isBlank() || UNKNOWN.equals(value)) return UNKNOWN;
        if (value.contains(".")) {
            String[] parts = value.split("\\.");
            return parts.length == 4 ? parts[0] + "." + parts[1] + "." + parts[2] + ".x" : "Masked";
        }
        if (value.contains(":")) {
            String[] parts = value.split(":");
            return String.join(":", java.util.Arrays.stream(parts).limit(4).toList()) + "::/64";
        }
        return "Masked";
    }

    private String country(HttpServletRequest request) {
        if (!properties.isTrustProxyHeaders()) return UNKNOWN;
        for (String header : COUNTRY_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && value.matches("[A-Za-z]{2}")) return value.toUpperCase(Locale.ROOT);
        }
        return UNKNOWN;
    }

    private String location(String country, String timezone, String locale) {
        String safeTimezone = clean(timezone, 80, null);
        String safeLocale = clean(locale, 40, null);
        if (!UNKNOWN.equals(country) && safeTimezone != null) return country + " / " + safeTimezone;
        if (!UNKNOWN.equals(country)) return country;
        if (safeTimezone != null) return safeTimezone;
        if (safeLocale != null) return safeLocale;
        return UNKNOWN;
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) return "/";
        return value.replaceAll("(?i)/[0-9a-f]{8}-[0-9a-f-]{27,}", "/{id}")
                .replaceAll("/[A-Za-z0-9_-]{20,}(?=/|$)", "/{id}")
                .replaceAll("/\\d+(?=/|$)", "/{id}");
    }

    private String normalizePagePath(String value) {
        if (value == null || !value.startsWith("/") || value.length() > 120) return "/unknown";
        return normalizePath(value.split("[?#]", 2)[0]);
    }

    private String safeUser(String value) {
        if (value == null || value.isBlank()) return UNKNOWN;
        if ("Anonymous".equals(value)) return value;
        int at = value.indexOf('@');
        if (at <= 1) return value.length() <= 2 ? "**" : value.substring(0, 1) + "***";
        return value.substring(0, Math.min(2, at)) + "***" + value.substring(at);
    }

    private String clean(String value, int maxLength, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private Instant retentionCutoff(Instant now) {
        return now.minus(Duration.ofHours(Math.max(1, properties.getRetentionHours())));
    }

    private List<RequestSnapshot> recentRequests(Instant cutoff) {
        return requests.stream().filter(item -> !item.timestamp().isBefore(cutoff)).toList();
    }

    private <T> void addBounded(ConcurrentLinkedDeque<T> values, AtomicInteger size, T value) {
        values.addFirst(value);
        int current = size.incrementAndGet();
        int max = Math.max(100, properties.getMaxEvents());
        while (current > max) {
            if (values.pollLast() != null) current = size.decrementAndGet();
            else break;
        }
    }

    private long number(Long value) {
        return value == null ? 0 : value;
    }

    private double percent(double value) {
        return value < 0 ? 0 : round(value * 100);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record RequestSnapshot(
            Instant timestamp, String method, String path, int status, long durationMs,
            String clientIp, String country, String location, String userIdentity, String userAgent) {
    }

    private record PageSnapshot(
            Instant timestamp, String path, String userIdentity, String country, String location) {
    }

    private record UserMetricsCache(
            Instant expiresAt, AdminMonitoringResponse.UserMetrics metrics) {
    }
}