package com.trackai.backend.entity;

import com.trackai.backend.enums.CoverLetterStyle;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cover_letter_projects", indexes = {
        @Index(name = "idx_cover_letter_user_updated", columnList = "user_id, updated_at"),
        @Index(name = "idx_cover_letter_resume", columnList = "resume_project_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverLetterProject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "resume_project_id", nullable = false)
    private String resumeProjectId;

    @Column(nullable = false, length = 140)
    private String company;

    @Column(nullable = false, length = 140)
    private String role;

    @Column(name = "job_description", nullable = false, columnDefinition = "TEXT")
    private String jobDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CoverLetterStyle style;

    @Column(name = "model_id", nullable = false, length = 100)
    private String modelId;

    @Column(name = "last_instructions", columnDefinition = "TEXT")
    private String lastInstructions;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

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
