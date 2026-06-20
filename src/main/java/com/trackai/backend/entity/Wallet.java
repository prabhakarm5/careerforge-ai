package com.trackai.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private Long totalTokens;

    @Column(nullable = false)
    private Long usedTokens;

    @Column(nullable = false)
    private Long remainingTokens;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}