package com.trackai.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Long tokens;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Boolean active = true;

    private LocalDateTime createdAt;
}