package com.trackai.backend.entity;

import com.trackai.backend.enums.SupportTicketCategory;
import com.trackai.backend.enums.SupportTicketPriority;
import com.trackai.backend.enums.SupportTicketStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_tickets", indexes = {
        @Index(name = "idx_support_user_updated", columnList = "user_id, updated_at"),
        @Index(name = "idx_support_status_updated", columnList = "status, updated_at"),
        @Index(name = "idx_support_order", columnList = "order_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicket {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 140)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportTicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportTicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportTicketStatus status;

    private String orderId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime resolvedAt;

    @Version
    private Long version;
}