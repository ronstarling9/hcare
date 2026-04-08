package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayerRepository extends JpaRepository<Payer, UUID> {
    List<Payer> findByAgencyId(UUID agencyId);
    Optional<Payer> findByIdAndAgencyId(UUID id, UUID agencyId);
}
