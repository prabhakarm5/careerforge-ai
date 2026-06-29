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

    @Column(nullable = false, length = 3000)
    private String prompt;

    @Column(length = 3000)
    private String negativePrompt;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String aspectRatio;

    @Column(nullable = false, length = 3000)
    private String imageUrl;

    @Column(nullable = false)
    private Long tokensUsed;

    @Column(nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDateTime createdAt;

    private Integer width;

    private Integer height;

    private String mimeType;

    private Long imageSize;

    @Builder.Default
    private Boolean favorite = false;

    private String provider;

    private String providerImageId;

    private String storageUrl;

    @Column
    private String cloudinaryPublicId;

    private String format;

    private Long bytes;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

}