package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaregiverClientAffinityRepository extends JpaRepository<CaregiverClientAffinity, UUID> {
    List<CaregiverClientAffinity> findByScoringProfileId(UUID scoringProfileId);
    Optional<CaregiverClientAffinity> findByScoringProfileIdAndClientId(UUID scoringProfileId, UUID clientId);
}
