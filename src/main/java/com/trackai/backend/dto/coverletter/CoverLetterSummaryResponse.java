package com.trackai.backend.dto.coverletter;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CoverLetterSummaryResponse {
    private String id;
    private String company;
    private String role;
    private String style;
    private String styleLabel;
    private String modelLabel;
    private LocalDateTime updatedAt;
}
