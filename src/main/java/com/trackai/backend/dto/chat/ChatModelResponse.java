package com.trackai.backend.dto.chat;

public record ChatModelResponse(
        String id,
        String label,
        String description,
        boolean vision,
        String type,
        boolean premium,
        long minimumCredits,
        boolean locked,
        String lockedReason) {
}
