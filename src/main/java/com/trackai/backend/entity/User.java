package com.trackai.backend.entity;

import com.trackai.backend.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    /*
     * ==========================================================
     * PostgreSQL + MySQL (ACTIVE)
     * ==========================================================
     * EnumType.STRING works perfectly with both databases.
     */
    @Enumerated(EnumType.STRING)
    private Role role;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean blocked = false;

    @Column(nullable = false, unique = true)
    private String mobileNumber;

    /*
     * ==========================================================
     * PostgreSQL + MySQL (ACTIVE)
     * ==========================================================
     * VARCHAR(500) works in both databases.
     * If in future description becomes large,
     * switch to @Lob + TEXT.
     */
    @Column(length = 500)
    private String description;

    /*
     * ==========================================================
     * PostgreSQL Alternative (Optional)
     * ==========================================================
     */

    // @Lob
    // @Column(columnDefinition = "TEXT")
    // private String description;

    @Builder.Default
    @Column(nullable = false)
    private Boolean emailVerified = false;

    private String profileImage;

    private LocalDateTime passwordChangedAt;

    // User.java entity ke andar profileImage field ke paas hi ye naya field add
    // karo
    @Column(name = "profile_image_public_id")
    private String profileImagePublicId; // Cloudinary ka asli public_id yahan store hoga (URL nahi)

    /*
     * ==========================================================
     * MySQL (NOTE)
     * ==========================================================
     * No database-specific changes required.
     * This entity is fully compatible with PostgreSQL and MySQL.
     */
}