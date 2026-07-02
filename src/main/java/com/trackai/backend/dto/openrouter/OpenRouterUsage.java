package com.trackai.backend.dto.openrouter;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenRouterUsage {

    private Integer prompt_tokens;

    private Integer completion_tokens;

    private Integer total_tokens;

}