package com.trackai.backend.dto.groq;

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
public class GroqRequest {

    private String model;

    private List<GroqMessage> messages;

    /**
     * When true, Groq returns Server-Sent Events (text/event-stream) with
     * incremental "delta" content chunks instead of one full JSON response.
     * Left null/false for the existing non-streaming calls (generateResponse,
     * generateTitle) so their behavior is unchanged.
     */
    private Boolean stream;

    /**
     * Maximum tokens Groq is allowed to generate for this response.
     * Without this set explicitly, the provider falls back to its own
     * default, which can be small enough to silently truncate longer
     * answers (finish_reason: "length") well before the user expects it
     * to stop. GroqServiceImpl now always sets this — via
     * groqModelConfig.getMaxTokens() for normal chat, or a small fixed
     * value (30) for title generation.
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;
}