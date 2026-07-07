package com.trackai.backend.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenResponse {

    // ✅ FIX — ye bhi cookie mein hi jaata hai (addAccessTokenCookie),
    // body mein bhejne ki zaroorat nahi
    @JsonIgnore
    private String accessToken;

    // ✅ pehle se sahi
    @JsonIgnore
    private String refreshToken;

    private String tokenType;

    private String message;
}