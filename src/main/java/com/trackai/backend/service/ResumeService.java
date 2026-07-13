package com.trackai.backend.service;

import com.trackai.backend.dto.resume.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ResumeService {

    ResumeProjectResponse analyze(MultipartFile resume, String jobDescription, String model);

    ResumeProjectResponse matchJob(String projectId, JobDescriptionRequest request);

    ResumeChatResponse chat(String projectId, ResumeChatRequest request);

    Flux<String> streamChat(String projectId, ResumeChatRequest request);

    GeneratedResumeResponse generate(String projectId, GenerateResumeRequest request);

    ResumeProjectResponse getProject(String projectId);

    List<ResumeProjectSummaryResponse> getProjects();

    List<ResumeModelResponse> getModels();

    ResumeDownload download(String projectId);

    void delete(String projectId);

    record ResumeDownload(byte[] bytes, String fileName) {
    }
}
