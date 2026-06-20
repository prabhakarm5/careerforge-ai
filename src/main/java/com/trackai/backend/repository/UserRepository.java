package com.trackai.backend.repository;

import com.trackai.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findByMobileNumber(
            String mobileNumber);
}