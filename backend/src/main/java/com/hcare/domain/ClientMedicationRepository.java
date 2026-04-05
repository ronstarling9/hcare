package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ClientMedicationRepository extends JpaRepository<ClientMedication, UUID> {
    List<ClientMedication> findByClientId(UUID clientId);
}
