package com.trackai.backend.dto;

import lombok.*;

@Getter
@Setter

@NoArgsConstructor
@AllArgsConstructor

@Builder
public class RefreshTokenResponse {

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    private String message;
}