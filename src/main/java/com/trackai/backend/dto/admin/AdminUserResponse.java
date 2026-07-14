package com.trackai.backend.dto.admin;

import com.trackai.backend.entity.User;
import com.trackai.backend.enums.Role;

import java.time.LocalDateTime;

public record AdminUserResponse(
        String id,
        String name,
        String email,
        String role,
        LocalDateTime createdAt,
        boolean enabled,
        boolean blocked,
        boolean emailVerified,
        String profileImage) {

    public AdminUserResponse(
            String id,
            String name,
            String email,
            Role role,
            LocalDateTime createdAt,
            Boolean enabled,
            Boolean blocked,
            Boolean emailVerified,
            String profileImage) {
        this(
                id,
                name,
                email,
                role == null ? "UNKNOWN" : role.name(),
                createdAt,
                Boolean.TRUE.equals(enabled),
                Boolean.TRUE.equals(blocked),
                Boolean.TRUE.equals(emailVerified),
                profileImage);
    }

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole() == null ? "UNKNOWN" : user.getRole().name(),
                user.getCreatedAt(),
                Boolean.TRUE.equals(user.getEnabled()),
                Boolean.TRUE.equals(user.getBlocked()),
                Boolean.TRUE.equals(user.getEmailVerified()),
                user.getProfileImage());
    }
}
