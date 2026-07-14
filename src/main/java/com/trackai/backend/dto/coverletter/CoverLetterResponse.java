package com.trackai.backend.dto.coverletter;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CoverLetterResponse {
    private String id;
    private String resumeProjectId;
    private String resumeFileName;
    private String company;
    private String role;
    private String jobDescription;
    private String style;
    private String styleLabel;
    private String modelId;
    private String modelLabel;
    private String content;
    private String lastInstructions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String pdfDownloadUrl;
    private String docxDownloadUrl;
}
