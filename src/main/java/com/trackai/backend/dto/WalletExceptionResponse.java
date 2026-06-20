package com.trackai.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class WalletExceptionResponse {

    private boolean success;

    private String message;

    private String path;

    private LocalDateTime timestamp;
}