package com.trackai.backend.service;

import com.trackai.backend.dto.job.JobSearchResponse;

public interface JobSearchService {
    JobSearchResponse search(String query, String location, String country, int page);
}