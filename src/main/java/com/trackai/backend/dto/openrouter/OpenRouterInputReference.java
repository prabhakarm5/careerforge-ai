package com.trackai.backend.dto.openrouter;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OpenRouterInputReference {

    private String type;

    @JsonProperty("mime_type")
    private String mimeType;

    private String data;

}