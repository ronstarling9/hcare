package com.hcare.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PhiAuditRepository extends JpaRepository<PhiAuditLog, UUID> {
    List<PhiAuditLog> findByAgencyId(UUID agencyId);
}
