package com.trackai.backend.dto.cache;

import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CachedConversation implements Serializable {
    private String id;
    private String userId;
    private String title;
    private String featureType;
    private Boolean archived;
    private Boolean pinned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}