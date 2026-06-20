package com.trackai.backend.security;

import com.trackai.backend.entity.User;
import com.trackai.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService

                implements UserDetailsService {

        private final UserRepository userRepository;

        // NORMALIZE EMAIL
        private String normalizeEmail(
                        String email) {

                return email

                                .trim()

                                .toLowerCase();
        }

        @Override
        public UserDetails loadUserByUsername(
                        String email)

                        throws UsernameNotFoundException {

                // NORMALIZE EMAIL
                email = normalizeEmail(email);

                // FIND USER
                User user = userRepository

                                .findByEmail(email)

                                .orElseThrow(() ->

                                new UsernameNotFoundException(
                                                "User not found"));

                return new org.springframework.security.core.userdetails.User(

                                user.getEmail(),

                                user.getPassword(),

                                List.of(

                                                new SimpleGrantedAuthority(

                                                                user.getRole()
                                                                                .name())));
        }
}