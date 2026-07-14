package com.trackai.backend.entity;

import com.trackai.backend.enums.InterviewDifficulty;
import com.trackai.backend.enums.InterviewStatus;
import com.trackai.backend.enums.InterviewType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_sessions", indexes = {
        @Index(name = "idx_interview_user_updated", columnList = "user_id, updated_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "resume_project_id")
    private String resumeProjectId;

    @Column(nullable = false, length = 140)
    private String role;

    @Column(length = 140)
    private String company;

    @Column(name = "job_description", nullable = false, columnDefinition = "TEXT")
    private String jobDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InterviewType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InterviewDifficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InterviewStatus status;

    @Column(name = "model_id", nullable = false, length = 100)
    private String modelId;

    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions;

    @Column(name = "current_question_number", nullable = false)
    private Integer currentQuestionNumber;

    @Column(name = "current_question", nullable = false, columnDefinition = "TEXT")
    private String currentQuestion;

    @Column(name = "current_focus", length = 255)
    private String currentFocus;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
