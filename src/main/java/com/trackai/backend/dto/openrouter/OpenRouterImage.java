package com.trackai.backend.dto.openrouter;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenRouterImage {

    private String url;

    @JsonProperty("b64_json")
    private String b64Json;

}