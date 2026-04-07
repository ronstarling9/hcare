package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvvRecordRepository extends JpaRepository<EvvRecord, UUID> {
    Optional<EvvRecord> findByShiftId(UUID shiftId);
    List<EvvRecord> findByShiftIdIn(Collection<UUID> shiftIds);
}
