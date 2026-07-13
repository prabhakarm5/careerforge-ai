package com.trackai.backend.dto.resume;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ResumeProjectSummaryResponse {
    private String id;
    private String fileName;
    private String status;
    private String modelId;
    private String modelLabel;
    private Integer atsScore;
    private Integer matchScore;
    private boolean jobDescriptionProvided;
    private LocalDateTime updatedAt;
}
