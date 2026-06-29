package com.trackai.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import com.trackai.backend.dto.cloudinary.CloudinaryUploadResponse;
import com.trackai.backend.enums.Role;

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

    @Enumerated(EnumType.STRING)
    private Role role;

    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Boolean enabled = false;

    @Column(nullable = false)
    private Boolean blocked = false;

    @Column(nullable = false, unique = true)
    private String mobileNumber;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Boolean emailVerified = false;

    private String profileImage;

    @Column
    LocalDateTime passwordChangedAt;
}