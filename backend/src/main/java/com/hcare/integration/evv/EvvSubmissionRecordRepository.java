package com.hcare.integration.evv;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvvSubmissionRecordRepository extends JpaRepository<EvvSubmissionRecord, UUID> {

    Optional<EvvSubmissionRecord> findByEvvRecordId(UUID evvRecordId);

    List<EvvSubmissionRecord> findByAgencyIdAndStatus(UUID agencyId, String status);

    List<EvvSubmissionRecord> findBySubmissionModeAndStatus(String submissionMode, String status);
}
