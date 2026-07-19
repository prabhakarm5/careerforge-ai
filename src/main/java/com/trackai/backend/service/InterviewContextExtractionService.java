package com.trackai.backend.service;

import com.trackai.backend.dto.interview.InterviewContextExtractionResponse;
import org.springframework.web.multipart.MultipartFile;

public interface InterviewContextExtractionService {

    default InterviewContextExtractionResponse extract(MultipartFile file, String model) {
        return extract(file, model, "JOB_DESCRIPTION");
    }

    InterviewContextExtractionResponse extract(MultipartFile file, String model, String contextType);
}