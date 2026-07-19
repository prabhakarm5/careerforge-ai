package com.trackai.backend.dto.interview;

public record InterviewContextExtractionResponse(
        String fileName,
        String text,
        String sourceType,
        long chargedTokens) {
}