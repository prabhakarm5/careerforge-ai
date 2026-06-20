package com.trackai.backend.dto.groq;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GroqResponse {

    private List<ChoiceDto> choices;

    private UsageDto usage;
}