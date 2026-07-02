package com.trackai.backend.dto.openrouter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenRouterGenerateRequest {

    private String model;

    private String prompt;

    @JsonProperty("input_references")
    private List<OpenRouterInputReference> inputReferences;

}