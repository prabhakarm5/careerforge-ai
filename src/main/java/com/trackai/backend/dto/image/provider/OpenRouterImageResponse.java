package com.trackai.backend.dto.image.provider;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OpenRouterImageResponse {

    private List<ImageData> data;

    @Getter
    @Setter
    public static class ImageData {

        private String url;

        private String b64_json;

    }

}