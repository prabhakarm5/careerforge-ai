package com.trackai.backend.dto.groq;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChoiceDto {

    private GroqResponseMessage message;
}