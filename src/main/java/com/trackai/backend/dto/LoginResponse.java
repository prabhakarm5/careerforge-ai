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

    // ✅ FIX — cookie mein jaata hai, response body mein nahi jaana chahiye
    @JsonIgnore
    private String accessToken;

    // ✅ pehle se sahi tha — field zaroori hai kyunki
    // AuthServiceImpl/AdminOtpLoginServiceImpl .refreshToken(refreshToken)
    // builder method use karte hain, lekin JSON mein kabhi expose nahi hoga
    @JsonIgnore
    private String refreshToken;

    // ✅ FIX — ye bhi cookie mein jaata hai (addFingerprintCookie), isliye
    // body mein bhejne ki zaroorat nahi
    @JsonIgnore
    private String fingerprint;

    private String profileImage;
}