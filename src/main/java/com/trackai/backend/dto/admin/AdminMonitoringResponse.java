package com.trackai.backend.dto.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdminMonitoringResponse(
        Instant generatedAt,
        SystemMetrics system,
        UserMetrics users,
        TrafficMetrics traffic,
        List<MetricPoint> requestTimeline,
        List<NamedMetric> topEndpoints,
        List<NamedMetric> topPages,
        List<NamedMetric> countries,
        List<EndpointPerformance> slowEndpoints,
        Map<String, Long> statusCodes,
        List<RequestEntry> recentRequests) {

    public record SystemMetrics(
            double processCpuPercent,
            double systemCpuPercent,
            long usedHeapBytes,
            long maxHeapBytes,
            int liveThreads,
            long uptimeSeconds) {
    }

    public record UserMetrics(
            long total,
            long enabled,
            long blocked,
            long verified,
            long joinedLastSevenDays) {
    }

    public record TrafficMetrics(
            long requests,
            long errors,
            double errorRate,
            double averageLatencyMs,
            long pageViews,
            int activeUsers) {
    }

    public record MetricPoint(String label, long value) {
    }

    public record NamedMetric(String name, long value) {
    }

    public record EndpointPerformance(
            String path,
            long requests,
            double averageLatencyMs,
            long p95LatencyMs,
            long maxLatencyMs,
            long errors) {
    }

    public record RequestEntry(
            Instant timestamp,
            String method,
            String path,
            int status,
            long durationMs,
            String maskedIp,
            String country,
            String user,
            String clientIp,
            String location,
            String userAgent,
            String userEmail,
            String responseSummary,
            String contentType,
            long responseBytes) {
    }
}