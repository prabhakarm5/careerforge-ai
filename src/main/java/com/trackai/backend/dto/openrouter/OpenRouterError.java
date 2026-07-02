package com.trackai.backend.dto.openrouter;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenRouterError {

    private String message;

    private String type;

    private String code;

}