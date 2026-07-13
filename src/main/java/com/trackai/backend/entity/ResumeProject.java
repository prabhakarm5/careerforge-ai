package com.trackai.backend.entity;

import com.trackai.backend.enums.ResumeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "resume_projects", indexes = {
        @Index(name = "idx_resume_project_user_updated", columnList = "user_id, updated_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeProject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "source_mime_type", nullable = false, length = 120)
    private String sourceMimeType;

    // Nullable keeps resume projects created before multi-model support compatible.
    @Column(name = "model_id", length = 100)
    private String modelId;
    @Column(name = "resume_text", nullable = false, columnDefinition = "TEXT")
    private String resumeText;
    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;
    @Column(name = "analysis_json", nullable = false, columnDefinition = "TEXT")
    private String analysisJson;
    @Column(name = "generated_resume_json", columnDefinition = "TEXT")
    private String generatedResumeJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResumeStatus status;

    @Column(name = "ats_score", nullable = false)
    private Integer atsScore;

    @Column(name = "match_score")
    private Integer matchScore;

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
