package com.trackai.backend.service;

import java.util.List;
import com.trackai.backend.dto.chat.ChatResponse;
import com.trackai.backend.dto.groq.GroqMessage;

public interface GroqService {

    ChatResponse generateResponse(List<GroqMessage> messages);

    String generateTitle(
            String prompt);

}