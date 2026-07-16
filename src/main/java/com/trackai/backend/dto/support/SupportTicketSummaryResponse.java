package com.trackai.backend.dto.support;

import com.trackai.backend.enums.SupportTicketCategory;
import com.trackai.backend.enums.SupportTicketPriority;
import com.trackai.backend.enums.SupportTicketStatus;

import java.time.LocalDateTime;

public record SupportTicketSummaryResponse(
        String id,
        String userId,
        String userName,
        String userEmail,
        String subject,
        SupportTicketCategory category,
        SupportTicketPriority priority,
        SupportTicketStatus status,
        String orderId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime resolvedAt) {
}