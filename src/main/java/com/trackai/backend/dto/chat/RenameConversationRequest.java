package com.trackai.backend.dto.chat;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RenameConversationRequest {

    @NotBlank
    private String title;

}
