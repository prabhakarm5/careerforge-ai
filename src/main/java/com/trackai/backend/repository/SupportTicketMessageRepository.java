package com.trackai.backend.repository;

import com.trackai.backend.entity.SupportTicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, String> {
    List<SupportTicketMessage> findByTicketIdOrderByCreatedAtAsc(String ticketId);
}