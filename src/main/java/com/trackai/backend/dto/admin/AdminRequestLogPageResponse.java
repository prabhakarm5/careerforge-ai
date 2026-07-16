package com.trackai.backend.dto.admin;

import java.util.List;

public record AdminRequestLogPageResponse(
        List<AdminMonitoringResponse.RequestEntry> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
