package com.trackai.backend.dto.resume;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ResumeMessageResponse {
    private String id;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
