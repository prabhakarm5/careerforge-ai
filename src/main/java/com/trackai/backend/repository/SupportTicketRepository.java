package com.trackai.backend.repository;

import com.trackai.backend.entity.SupportTicket;
import com.trackai.backend.enums.SupportTicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, String> {
    List<SupportTicket> findTop100ByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<SupportTicket> findByIdAndUserId(String id, String userId);
    List<SupportTicket> findTop100ByOrderByUpdatedAtDesc();
    List<SupportTicket> findTop100ByStatusOrderByUpdatedAtDesc(SupportTicketStatus status);
}