package com.trackai.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Getter
@Setter
@Builder
public class LoginResponse {

    private String id;

    private String name;

    private String email;

    private String role;

    // ✅ FIX — @JsonIgnore YAHAN SE HATA DIYA (same reason as above)
    private String accessToken;

    // ✅ sahi hai — kabhi expose nahi karna
    @JsonIgnore
    private String refreshToken;

    // ✅ sahi hai — kabhi expose nahi karna
    @JsonIgnore
    private String fingerprint;

    private String profileImage;
}