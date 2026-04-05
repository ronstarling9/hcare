package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AgencyUserRepository extends JpaRepository<AgencyUser, UUID> {
    Optional<AgencyUser> findByEmail(String email);
}
