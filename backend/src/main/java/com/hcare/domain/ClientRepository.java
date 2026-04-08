package com.hcare.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    List<Client> findByAgencyId(UUID agencyId);
    Page<Client> findByAgencyId(UUID agencyId, Pageable pageable);
    Optional<Client> findByIdAndAgencyId(UUID id, UUID agencyId);
}
