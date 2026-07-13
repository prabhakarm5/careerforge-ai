package com.trackai.backend.dto.resume;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ResumeProjectResponse {
    private String id;
    private String fileName;
    private String status;
    private String modelId;
    private String modelLabel;
    private Integer atsScore;
    private Integer matchScore;
    private boolean jobDescriptionProvided;
    private JsonNode analysis;
    private JsonNode generatedResume;
    private List<ResumeMessageResponse> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String downloadUrl;
}
