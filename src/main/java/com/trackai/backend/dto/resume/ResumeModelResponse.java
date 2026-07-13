package com.trackai.backend.dto.resume;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResumeModelResponse {
    private String id;
    private String label;
    private String description;
    private String tier;
    private boolean preview;
    private boolean defaultModel;
}
