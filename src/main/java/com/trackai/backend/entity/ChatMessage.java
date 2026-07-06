package com.trackai.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    private String id;

    @Column(nullable = false)
    private String conversationId;

    @Column(nullable = false)
    private String role;

    /*
     * ==========================================================
     * PostgreSQL (ACTIVE)
     * ==========================================================
     * PostgreSQL doesn't support LONGTEXT.
     * TEXT can store very large text (up to ~1GB).
     */
    // @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    /*
     * ==========================================================
     * MySQL (COMMENTED)
     * ==========================================================
     * Uncomment this if switching back to MySQL.
     */

    // @Column(columnDefinition = "LONGTEXT")
    // private String content;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private LocalDateTime createdAt;
}