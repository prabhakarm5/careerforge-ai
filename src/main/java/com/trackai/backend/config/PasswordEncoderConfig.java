package com.trackai.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

        /**
         * BCrypt with explicit strength 12.
         * Keeping this bean outside SecurityConfig avoids a Spring bean cycle:
         * SecurityConfig -> OAuth2LoginSuccessHandler -> PasswordEncoder.
         */
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder(12);
        }
}