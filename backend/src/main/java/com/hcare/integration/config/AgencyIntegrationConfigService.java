package com.hcare.integration.config;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AgencyIntegrationConfigService {

    private final AgencyIntegrationConfigRepository repo;

    public AgencyIntegrationConfigService(AgencyIntegrationConfigRepository repo) {
        this.repo = repo;
    }

    /**
     * Saves a new integration config, enforcing the uniqueness invariant for
     * (agencyId, integrationType, stateCode, payerType) via a pessimistic-write lock
     * on the entity-returning query rather than a count query.
     */
    @Transactional
    public AgencyIntegrationConfig save(AgencyIntegrationConfig config) {
        if (repo.findForUpdate(config.getAgencyId(), config.getIntegrationType(),
                               config.getStateCode(), config.getPayerType()).isPresent()) {
            throw new DuplicateIntegrationConfigException(
                    "Config already exists for agencyId=%s type=%s stateCode=%s payerType=%s"
                            .formatted(config.getAgencyId(), config.getIntegrationType(),
                                       config.getStateCode(), config.getPayerType()));
        }
        return repo.save(config);
    }

    public Optional<AgencyIntegrationConfig> findActive(UUID agencyId, String integrationType) {
        return repo.findByAgencyIdAndIntegrationTypeAndActiveTrue(agencyId, integrationType);
    }
}
