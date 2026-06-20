package com.trackai.backend.dto;

import java.time.LocalDateTime;

import lombok.*;

@Getter
@Setter

@NoArgsConstructor
@AllArgsConstructor
@Builder

public class MessageResponse {

    private String message;

    private LocalDateTime resendAvailableAt;
}