package com.hcare.audit;

import com.hcare.AbstractIntegrationTest;
import com.hcare.domain.Agency;
import com.hcare.domain.AgencyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PhiAuditServiceIT extends AbstractIntegrationTest {

    @Autowired private PhiAuditService auditService;
    @Autowired private PhiAuditRepository auditRepository;
    @Autowired private AgencyRepository agencyRepository;

    @Test
    void logRead_persistsAuditEntry() {
        Agency agency = agencyRepository.save(new Agency("Audit Test Agency", "TX"));
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        auditService.logRead(userId, agency.getId(), ResourceType.CLIENT, resourceId, "127.0.0.1");

        var logs = auditRepository.findByAgencyId(agency.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(AuditAction.READ);
        assertThat(logs.get(0).getResourceType()).isEqualTo(ResourceType.CLIENT);
        assertThat(logs.get(0).getResourceId()).isEqualTo(resourceId);
        assertThat(logs.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void logWrite_persistsAuditEntry() {
        Agency agency = agencyRepository.save(new Agency("Audit Test Agency 2", "CA"));
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        auditService.logWrite(userId, agency.getId(), ResourceType.CAREPLAN, resourceId, "10.0.0.1");

        var logs = auditRepository.findByAgencyId(agency.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(AuditAction.WRITE);
    }
}
