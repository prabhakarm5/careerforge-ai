package com.trackai.backend.dto.groq;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class GroqRequest {

    private String model;

    private List<GroqMessage> messages;
}