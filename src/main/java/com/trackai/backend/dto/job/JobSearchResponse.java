package com.trackai.backend.dto.job;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class JobSearchResponse {
    private String query;
    private String location;
    private String country;
    private int page;
    private long totalResults;
    private String attribution;
    private List<JobListingResponse> jobs;
}