package com.trackai.backend.dto.groq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * NOTE: This is the minimal shape needed for streaming + truncation
 * detection. If your existing GroqStreamChunk.java has additional fields
 * (id, object, created, model, usage, etc.) keep them — just make sure
 * Choice has the finishReason field below added in. That field is the
 * actual fix for "answer stops midway": without it, the backend has no
 * way to know the model was cut off by max_tokens instead of finishing
 * naturally.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroqStreamChunk {

    private List<Choice> choices;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Delta delta;

        // 👇 THE FIX — was missing entirely before. Values from the API:
        // "stop" → model finished naturally, normal completion
        // "length" → model was cut off by max_tokens (THIS is the bug)
        // null → still streaming, more chunks coming
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delta {
        private String role;
        private String content;
    }
}