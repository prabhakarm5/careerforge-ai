package com.trackai.backend.dto.support;

import com.trackai.backend.enums.SupportTicketStatus;
import jakarta.validation.constraints.NotNull;

public record SupportStatusRequest(
        @NotNull SupportTicketStatus status) {
}