package com.trackai.backend.dto.job;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class JobListingResponse {
    private String id;
    private String title;
    private String company;
    private String location;
    private String description;
    private String category;
    private String contractType;
    private String contractTime;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private Instant postedAt;
    private String applyUrl;
    private String source;
}