package com.trackai.backend.service;

import com.trackai.backend.dto.chat.ChatResponse;
import com.trackai.backend.dto.chat.SendMessageRequest;

public interface ChatService {

    ChatResponse sendMessage(
            SendMessageRequest request);

}