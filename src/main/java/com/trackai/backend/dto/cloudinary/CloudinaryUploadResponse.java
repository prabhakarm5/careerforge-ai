package com.trackai.backend.dto.cloudinary;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CloudinaryUploadResponse {

    private String secureUrl;

    private String publicId;

    private Integer width;

    private Integer height;

    private String format;

    private Long bytes;

}