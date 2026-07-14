package com.trackai.backend.repository;

import com.trackai.backend.dto.admin.AdminUserResponse;
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

    @Query("""
            select new com.trackai.backend.dto.admin.AdminUserResponse(
                    u.id, u.name, u.email, u.role, u.createdAt,
                    u.enabled, u.blocked, u.emailVerified, u.profileImage)
            from User u
            """)
    Page<AdminUserResponse> findAdminUsers(Pageable pageable);

    @Query("""
            select new com.trackai.backend.dto.admin.AdminUserResponse(
                    u.id, u.name, u.email, u.role, u.createdAt,
                    u.enabled, u.blocked, u.emailVerified, u.profileImage)
            from User u
            where lower(u.email) like lower(concat('%', :search, '%'))
               or lower(u.name) like lower(concat('%', :search, '%'))
            """)
    Page<AdminUserResponse> searchAdminUsers(
            @Param("search") String search,
            Pageable pageable);

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