package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ClientDiagnosisRepository extends JpaRepository<ClientDiagnosis, UUID> {
    List<ClientDiagnosis> findByClientId(UUID clientId);
}
