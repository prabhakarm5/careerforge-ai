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

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "LONGTEXT")
    private String response;

    @Column(nullable = false)
    private Integer promptTokens;

    @Column(nullable = false)
    private Integer completionTokens;

    @Column(nullable = false)
    private Integer totalTokens;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}