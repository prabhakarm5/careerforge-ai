package com.trackai.backend.dto.interview;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class LiveInterviewTokenResponse {
    private String token;
    private String model;
    private String voice;
    private Instant expiresAt;
}
