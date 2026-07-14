package com.trackai.backend.dto.support;

import java.util.List;

public record SupportTicketResponse(
        SupportTicketSummaryResponse ticket,
        List<SupportMessageResponse> messages) {
}