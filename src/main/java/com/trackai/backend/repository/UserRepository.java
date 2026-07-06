package com.trackai.backend.repository;

import com.trackai.backend.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    /*
     * ==========================================================
     * PostgreSQL ✅
     * MySQL ✅
     *
     * Ye repository PostgreSQL aur MySQL dono me
     * fully compatible hai.
     *
     * Koi bhi database-specific query use nahi hui hai.
     *
     * Future Notes:
     * - Agar native SQL (@Query(nativeQuery = true)) use karoge,
     * tab PostgreSQL aur MySQL ke syntax alag ho sakte hain.
     * - Derived Query Methods (findBy...) dono databases me
     * same tarah kaam karte hain.
     * ==========================================================
     */

    // Find user by Email
    Optional<User> findByEmail(String email);

    // Find user by Mobile Number
    Optional<User> findByMobileNumber(String mobileNumber);

}