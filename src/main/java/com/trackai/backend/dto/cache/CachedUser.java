package com.trackai.backend.dto.cache;

import com.trackai.backend.enums.Role;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CachedUser implements Serializable {

    /*
     * ============================================================
     * Redis Cache DTO
     * ============================================================
     *
     * Purpose:
     * Store frequently used user information in Redis
     * so database is not queried on every request.
     *
     * Only NON-SENSITIVE fields are cached.
     *
     * Passwords, Tokens, OTPs are NEVER cached.
     */

    // Primary Key
    private String id;

    // Basic Info
    private String name;

    private String email;

    private String mobileNumber;

    // Profile
    private String profileImage;

    private String description;

    // Security
    private Role role;

    private Boolean enabled;

    private Boolean blocked;

    private Boolean emailVerified;

    // Audit
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /*
     * ============================================================
     * NOT CACHED
     * ============================================================
     *
     * password
     * refreshToken
     * verificationToken
     * resetPasswordToken
     * otp
     * loginAttempts
     * wallet
     * conversations
     * chatMessages
     *
     * Reason:
     * Sensitive data
     * OR
     * Very frequently changing
     * OR
     * Large object graph
     *
     */
}