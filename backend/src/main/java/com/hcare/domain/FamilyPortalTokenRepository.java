package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface FamilyPortalTokenRepository extends JpaRepository<FamilyPortalToken, UUID> {

    Optional<FamilyPortalToken> findByTokenHash(String tokenHash);

    /**
     * Bulk-deletes expired rows. Called nightly by FamilyPortalTokenCleanupJob.
     * agencyFilter does NOT apply here (FamilyPortalToken has no @Filter) — explicit
     * JPQL delete is safe without filter injection.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM FamilyPortalToken t WHERE t.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}
