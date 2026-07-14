package com.trackai.backend.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenResponse {

    // ✅ FIX — @JsonIgnore YAHAN SE HATA DIYA
    // Frontend ka Zustand store (in-memory) is field ko response body
    // se hi utha ke rakhta hai — JwtFilter cookie se access token
    // padhta hi nahi, sirf Authorization header se padhta hai.
    // Isliye body mein bhejna zaroori hai, warna "Bearer undefined"
    // banta hai (0 dots) aur har protected API 401 deta hai.
    private String accessToken;

    // ✅ ye sahi hai — refreshToken sirf cookie mein hi rehna chahiye,
    // JS isko kabhi na chhue (XSS safety)
    @JsonIgnore
    private String refreshToken;

    private String tokenType;

    // Server-authoritative role used while restoring a browser tab.
    private String role;

    private String message;
}