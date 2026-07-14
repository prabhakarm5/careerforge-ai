package com.trackai.backend.service;

import com.trackai.backend.dto.support.*;
import com.trackai.backend.enums.SupportTicketStatus;

import java.util.List;

public interface SupportTicketService {
    SupportTicketResponse create(CreateSupportTicketRequest request);
    List<SupportTicketSummaryResponse> getMine();
    SupportTicketResponse getMine(String ticketId);
    SupportTicketResponse replyAsUser(String ticketId, SupportReplyRequest request);
    SupportTicketResponse resolveMine(String ticketId);
    SupportTicketResponse reopenMine(String ticketId);

    List<SupportTicketSummaryResponse> getAll(SupportTicketStatus status);
    SupportTicketResponse getAsAdmin(String ticketId);
    SupportTicketResponse replyAsAdmin(String ticketId, SupportReplyRequest request);
    SupportTicketResponse updateStatusAsAdmin(String ticketId, SupportStatusRequest request);
}