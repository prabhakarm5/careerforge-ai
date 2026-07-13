package com.trackai.backend.repository;

import com.trackai.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findByMobileNumber(String mobileNumber);

    Page<User> findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(
            String email, String name, Pageable pageable);

    // A single aggregate query keeps the live admin dashboard inexpensive.
    @Query("""
            select count(u) as total,
                   sum(case when u.enabled = true then 1 else 0 end) as enabledUsers,
                   sum(case when u.blocked = true then 1 else 0 end) as blockedUsers,
                   sum(case when u.emailVerified = true then 1 else 0 end) as verifiedUsers,
                   sum(case when u.createdAt >= :since then 1 else 0 end) as recentUsers
            from User u
            """)
    AdminUserMetricsView getAdminUserMetrics(@Param("since") LocalDateTime since);

    interface AdminUserMetricsView {
        Long getTotal();
        Long getEnabledUsers();
        Long getBlockedUsers();
        Long getVerifiedUsers();
        Long getRecentUsers();
    }
}