package com.trackai.backend.enums;

import lombok.Getter;

@Getter
public enum CoverLetterStyle {
    PROFESSIONAL("Professional", "Balanced, polished and suitable for most roles"),
    CONCISE("Concise", "Direct and compact for busy hiring teams"),
    ENTHUSIASTIC("Enthusiastic", "Warm, energetic and mission-focused"),
    EXECUTIVE("Executive", "Confident, strategic and leadership-oriented"),
    CREATIVE("Creative", "Distinctive while remaining professional and ATS-safe");

    private final String label;
    private final String description;

    CoverLetterStyle(String label, String description) {
        this.label = label;
        this.description = description;
    }
}
