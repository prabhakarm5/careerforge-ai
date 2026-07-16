package com.trackai.backend.repository;

import com.trackai.backend.entity.PromoCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, String> {
    Optional<PromoCode> findByCodeIgnoreCase(String code);
    @EntityGraph(attributePaths = "targetUserEmails")
    List<PromoCode> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromoCode p where upper(p.code) = upper(:code)")
    Optional<PromoCode> findByCodeForUpdate(@Param("code") String code);
}