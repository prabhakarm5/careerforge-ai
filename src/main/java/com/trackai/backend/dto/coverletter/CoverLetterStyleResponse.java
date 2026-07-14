package com.trackai.backend.dto.coverletter;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CoverLetterStyleResponse {
    private String id;
    private String label;
    private String description;
}
