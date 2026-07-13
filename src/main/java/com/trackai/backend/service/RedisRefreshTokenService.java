package com.trackai.backend.service;

public interface RedisRefreshTokenService {

        void saveRefreshToken(String email, String fingerprint, String refreshToken);

        boolean isValidRefreshToken(String email, String fingerprint, String refreshToken);

        // Compare-and-set rotation avoids separate GET, DELETE and SET network calls.
        boolean rotateRefreshToken(
                        String email,
                        String fingerprint,
                        String currentRefreshToken,
                        String newRefreshToken);

        void deleteRefreshToken(String email, String fingerprint);

        void deleteAllRefreshTokens(String email);
}