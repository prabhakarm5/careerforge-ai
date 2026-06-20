package com.trackai.backend.service;

public interface RedisRefreshTokenService {

        // SAVE REFRESH TOKEN
        void saveRefreshToken(

                        String email,

                        String fingerprint,

                        String refreshToken);

        // VALIDATE REFRESH TOKEN
        boolean isValidRefreshToken(

                        String email,

                        String fingerprint,

                        String refreshToken);

        // DELETE REFRESH TOKEN
        void deleteRefreshToken(

                        String email,

                        String fingerprint);

        void deleteAllRefreshTokens(String email);
}