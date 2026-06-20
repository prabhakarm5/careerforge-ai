package com.trackai.backend.service;

public interface RedisEmailVerificationTokenService {

        // SAVE VERIFICATION TOKEN
        void saveVerificationToken(

                        String email,

                        String token);

        // GET EMAIL BY TOKEN
        String getEmailByToken(
                        String token);

        // DELETE TOKEN
        void deleteToken(
                        String token);
}