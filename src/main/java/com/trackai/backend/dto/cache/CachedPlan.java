package com.trackai.backend.dto.cache;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/*
 * ============================================================
 * Redis Cache DTO for Subscription Plans
 * ============================================================
 *
 * Purpose:
 * Plans rarely change (admin creates/updates them occasionally)
 * but are read VERY frequently (every user hitting pricing page,
 * every checkout flow, etc). Isliye ye best candidate hai caching
 * ke liye — high read, low write.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CachedPlan implements Serializable {

    private String id;

    private String name;

    private Long price;

    private Long tokens;

    private String description;

    private Boolean active;

    private LocalDateTime createdAt;
}