package com.trackai.backend.dto.coverletter;

import com.trackai.backend.enums.CoverLetterStyle;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegenerateCoverLetterRequest {
    private CoverLetterStyle style;

    @Size(max = 100)
    private String model;

    @Size(max = 4_000, message = "Instructions must be 4000 characters or less")
    private String instructions;
}
