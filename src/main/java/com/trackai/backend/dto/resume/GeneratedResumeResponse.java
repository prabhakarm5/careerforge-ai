package com.trackai.backend.dto.resume;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeneratedResumeResponse {
    private String projectId;
    private JsonNode resume;
    private String downloadUrl;
    private String fileName;
    private String message;
}
