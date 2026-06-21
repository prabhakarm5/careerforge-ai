package com.trackai.backend.entity;

import com.trackai.backend.enums.FeatureType;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")

@Getter
@Setter

@NoArgsConstructor
@AllArgsConstructor

@Builder
public class Conversation {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    private FeatureType featureType;

    @Column(nullable = false)
    private Boolean archived;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Boolean pinned = false;

}
