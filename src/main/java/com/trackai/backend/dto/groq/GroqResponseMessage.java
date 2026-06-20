package com.trackai.backend.dto.groq;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroqResponseMessage {

    private String role;

    private String content;
}