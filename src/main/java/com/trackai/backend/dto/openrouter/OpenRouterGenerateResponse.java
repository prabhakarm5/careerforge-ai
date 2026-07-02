package com.trackai.backend.dto.openrouter;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OpenRouterGenerateResponse {

    private List<OpenRouterImage> data;

}