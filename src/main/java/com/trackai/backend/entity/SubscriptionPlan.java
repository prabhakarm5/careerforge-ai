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

    /*
     * ==========================================================
     * PostgreSQL + MySQL (ACTIVE)
     * ==========================================================
     * VARCHAR(500) is supported by both databases.
     * If you expect descriptions larger than 500 characters,
     * use @Lob + TEXT instead.
     */
    @Column(length = 500)
    private String description;

    /*
     * ==========================================================
     * PostgreSQL Alternative (Optional)
     * ==========================================================
     * Uncomment this if you want unlimited description length.
     */

    // @Lob
    // @Column(columnDefinition = "TEXT")
    // private String description;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /*
     * ==========================================================
     * MySQL (NOTE)
     * ==========================================================
     * No database-specific changes required.
     * This entity is fully compatible with both PostgreSQL and MySQL.
     */
}