package com.trackai.backend.dto.admin;

import java.time.Instant;
import java.util.List;

public record AdminUserActivityResponse(
        AdminUserResponse user,
        long requestCount,
        long errorCount,
        double averageLatencyMs,
        Instant lastSeenAt,
        List<AdminMonitoringResponse.RequestEntry> requests) {
}