package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaregiverClientAffinityRepository extends JpaRepository<CaregiverClientAffinity, UUID> {
    List<CaregiverClientAffinity> findByScoringProfileId(UUID scoringProfileId);
    Optional<CaregiverClientAffinity> findByScoringProfileIdAndClientId(UUID scoringProfileId, UUID clientId);

    /**
     * Inserts a new affinity row with visitCount=0 if no row exists for the given
     * (scoring_profile_id, client_id) pair. Does nothing if one already exists.
     * Eliminates the DataIntegrityViolationException race in LocalScoringService.updateAffinity —
     * ON CONFLICT DO NOTHING is safe to call concurrently without poisoning the transaction.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO caregiver_client_affinities
            (id, scoring_profile_id, client_id, agency_id, visit_count, version)
        VALUES
            (gen_random_uuid(), :scoringProfileId, :clientId, :agencyId, 0, 0)
        ON CONFLICT (scoring_profile_id, client_id) DO NOTHING
        """, nativeQuery = true)
    void insertIfNotExists(@Param("scoringProfileId") UUID scoringProfileId,
                           @Param("clientId") UUID clientId,
                           @Param("agencyId") UUID agencyId);
}
