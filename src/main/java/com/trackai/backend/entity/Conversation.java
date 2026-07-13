package com.trackai.backend.entity;

import com.trackai.backend.enums.FeatureType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "idx_conversations_user_archived_updated", columnList = "userId, archived, updatedAt"),
        @Index(name = "idx_conversations_user_title", columnList = "userId, title")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String title;

    /*
     * ==========================================================
     * PostgreSQL + MySQL (ACTIVE)
     * ==========================================================
     * EnumType.STRING works perfectly with both PostgreSQL and MySQL.
     * No database-specific changes required.
     */
    @Enumerated(EnumType.STRING)
    private FeatureType featureType;

    @Column(nullable = false)
    private Boolean archived;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean pinned = false;

    /*
     * ==========================================================
     * MySQL (NOTE)
     * ==========================================================
     * No changes required for MySQL.
     * This entity is fully compatible with both databases.
     */
}