package com.trackai.backend.dto.support;

import com.trackai.backend.enums.SupportTicketCategory;
import com.trackai.backend.enums.SupportTicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
        @NotBlank @Size(max = 140) String subject,
        @NotNull SupportTicketCategory category,
        SupportTicketPriority priority,
        @Size(max = 120) String orderId,
        @NotBlank @Size(max = 5000) String message) {
}