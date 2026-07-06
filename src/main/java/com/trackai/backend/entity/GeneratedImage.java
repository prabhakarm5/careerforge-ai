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

    /*
     * ==========================================================
     * PostgreSQL (ACTIVE)
     * ==========================================================
     * TEXT stores very large strings.
     * Also works perfectly in MySQL.
     */
    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String prompt;

    /*
     * ==========================================================
     * PostgreSQL (ACTIVE)
     * ==========================================================
     * Original provider image URL.
     */
    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String imageUrl;

    /*
     * ==========================================================
     * PostgreSQL (ACTIVE)
     * ==========================================================
     * Cloudinary image URL.
     */
    // @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
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

    /*
     * ==========================================================
     * MySQL (COMMENT)
     * ==========================================================
     * Agar MySQL use karna ho to neeche wala approach bhi use kar sakte ho.
     * Dono databases me kaam karega.
     *
     * @Column(nullable = false, length = 4000)
     * private String prompt;
     *
     * @Column(nullable = false, length = 4000)
     * private String imageUrl;
     *
     * @Column(nullable = false, length = 4000)
     * private String storageUrl;
     */
}