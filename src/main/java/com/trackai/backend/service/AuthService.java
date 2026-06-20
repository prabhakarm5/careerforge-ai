package com.trackai.backend.service;

import com.trackai.backend.dto.LoginRequest;
import com.trackai.backend.dto.LoginResponse;
import com.trackai.backend.dto.RefreshTokenResponse;
import com.trackai.backend.dto.RegisterRequest;

import com.trackai.backend.entity.User;

public interface AuthService {

        // REGISTER USER
        User registerUser(
                        RegisterRequest request);

        // LOGIN USER
        LoginResponse loginUser(
                        LoginRequest request);

        // REFRESH ACCESS TOKEN
        RefreshTokenResponse refreshAccessToken(

                        String refreshToken,

                        String fingerprint);

        // LOGOUT USER
        void logout(

                        String refreshToken,

                        String fingerprint);

        // LOGOUT ALL DEVICES
        void logoutAllDevices(String refreshToken);
}