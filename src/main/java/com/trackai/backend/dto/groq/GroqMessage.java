package com.trackai.backend.dto.groq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class GroqMessage {

    private String role;

    private String content;
}