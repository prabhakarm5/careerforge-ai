package com.trackai.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatHistory {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    // PostgreSQL + MySQL Compatible
    @Column(columnDefinition = "TEXT")
    private String question;

    /*
     * ============================
     * PostgreSQL (ACTIVE)
     * ============================
     * PostgreSQL doesn't support LONGTEXT.
     * TEXT can store up to ~1GB of data.
     */
    // @Lob
    @Column(columnDefinition = "TEXT")
    private String response;

    /*
     * ============================
     * MySQL (COMMENTED)
     * ============================
     * Uncomment this if switching back to MySQL.
     */

    // @Column(columnDefinition = "LONGTEXT")
    // private String response;

    @Column(nullable = false)
    private Integer promptTokens;

    @Column(nullable = false)
    private Integer completionTokens;

    @Column(nullable = false)
    private Integer totalTokens;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}