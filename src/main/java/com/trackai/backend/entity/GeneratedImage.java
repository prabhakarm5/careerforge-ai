package com.trackai.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 4000)
    private String prompt;

    // Original provider URL
    @Column(nullable = false, length = 4000)
    private String imageUrl;

    // Cloudinary URL
    @Column(nullable = false, length = 4000)
    private String storageUrl;

    private String providerImageId;

    private String cloudinaryPublicId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private Long tokensUsed;

    @Column(nullable = false)
    private String status;

    @Builder.Default
    private Boolean favorite = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {

        createdAt = LocalDateTime.now();

    }

}