package com.trackai.backend.service;

import com.trackai.backend.dto.coverletter.*;

import java.util.List;

public interface CoverLetterService {
    CoverLetterResponse generate(GenerateCoverLetterRequest request);
    CoverLetterResponse regenerate(String id, RegenerateCoverLetterRequest request);
    CoverLetterResponse update(String id, UpdateCoverLetterRequest request);
    CoverLetterResponse get(String id);
    List<CoverLetterSummaryResponse> getHistory();
    List<CoverLetterStyleResponse> getStyles();
    DocumentDownload download(String id, String format);
    void delete(String id);

    record DocumentDownload(byte[] bytes, String fileName, String contentType) {}
}
