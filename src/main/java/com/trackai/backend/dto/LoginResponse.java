package com.trackai.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LoginResponse {

    private String id;

    private String name;

    private String email;

    private String role;

    private String accessToken;

    private String refreshToken;

    private String fingerprint;

    private String profileImage;
}