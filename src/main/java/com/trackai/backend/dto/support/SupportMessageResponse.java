package com.trackai.backend.dto.support;

import com.trackai.backend.enums.Role;

import java.time.LocalDateTime;

public record SupportMessageResponse(
        String id,
        String senderId,
        Role senderRole,
        String message,
        LocalDateTime createdAt) {
}