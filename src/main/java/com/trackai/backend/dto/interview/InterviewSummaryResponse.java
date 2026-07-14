package com.trackai.backend.dto.interview;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InterviewSummaryResponse {
    private String id;
    private String role;
    private String company;
    private String type;
    private String difficulty;
    private String status;
    private Integer overallScore;
    private Integer answeredQuestions;
    private Integer totalQuestions;
    private LocalDateTime updatedAt;
}
