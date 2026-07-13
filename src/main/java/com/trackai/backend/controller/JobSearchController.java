package com.trackai.backend.controller;

import com.trackai.backend.dto.job.JobSearchResponse;
import com.trackai.backend.service.JobSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/jobs")
public class JobSearchController {

    private final JobSearchService jobSearchService;

    @GetMapping("/search")
    public ResponseEntity<JobSearchResponse> search(
            @RequestParam @Size(max = 120) String query,
            @RequestParam(defaultValue = "") @Size(max = 120) String location,
            @RequestParam(defaultValue = "") @Size(max = 2) String country,
            @RequestParam(defaultValue = "1") @Min(1) @Max(20) int page) {
        return ResponseEntity.ok(jobSearchService.search(query, location, country, page));
    }
}