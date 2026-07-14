package com.trackai.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_turns", indexes = {
        @Index(name = "idx_interview_turn_session_number", columnList = "session_id, question_number")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewTurn {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "question_number", nullable = false)
    private Integer questionNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "question_focus", length = 255)
    private String questionFocus;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "strengths_json", nullable = false, columnDefinition = "TEXT")
    private String strengthsJson;

    @Column(name = "improvements_json", nullable = false, columnDefinition = "TEXT")
    private String improvementsJson;

    @Column(name = "ideal_answer", nullable = false, columnDefinition = "TEXT")
    private String idealAnswer;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
