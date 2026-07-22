package com.trackai.backend.repository;

import com.trackai.backend.entity.PromoClaim;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromoClaimRepository extends JpaRepository<PromoClaim, String> {
    Optional<PromoClaim> findByPromoCodeIdAndUserId(String promoCodeId, String userId);
    List<PromoClaim> findByUserId(String userId);
    long countByPromoCodeId(String promoCodeId);
    long deleteByPromoCodeId(String promoCodeId);

    @Query("select c.promoCodeId as promoCodeId, count(c) as claimCount from PromoClaim c group by c.promoCodeId")
    List<PromoClaimCount> countAllByPromoCode();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PromoClaim c where c.promoCodeId = :promoId and c.userId = :userId")
    Optional<PromoClaim> findForUpdate(@Param("promoId") String promoId, @Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PromoClaim c where c.id = :id")
    Optional<PromoClaim> findByIdForUpdate(@Param("id") String id);

    interface PromoClaimCount {
        String getPromoCodeId();
        long getClaimCount();
    }
}