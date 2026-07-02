package com.trackai.backend.dto.groq;

import com.fasterxml.jackson.annotation.JsonInclude;
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
}