package com.trackai.backend.dto.image;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ImageHistoryResponse {

    private String id;

    private String prompt;

    private String imageUrl;

    private String storageUrl;

    private String aspectRatio;

    private String model;

    private Long tokensUsed;

    private Integer width;

    private Integer height;

    private Boolean favorite;

    private LocalDateTime createdAt;

}