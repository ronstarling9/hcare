package com.hcare.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ClientDiagnosisRepository extends JpaRepository<ClientDiagnosis, UUID> {
    List<ClientDiagnosis> findByClientId(UUID clientId);

    Page<ClientDiagnosis> findByClientId(UUID clientId, Pageable pageable);
}
