package com.trackai.backend.entity;

import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    private FeatureType featureType;

    private String description;

    private LocalDateTime createdAt;
}