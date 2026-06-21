package com.trackai.backend.dto.chat;

import com.trackai.backend.enums.FeatureType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ConversationResponse {

    private String id;

    private String title;

    private FeatureType featureType;

    private Boolean archived;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}