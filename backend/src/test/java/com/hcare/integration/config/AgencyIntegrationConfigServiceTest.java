package com.hcare.integration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgencyIntegrationConfigServiceTest {

    @Mock
    private AgencyIntegrationConfigRepository repo;

    private AgencyIntegrationConfigService service;

    private static final UUID AGENCY_ID = UUID.randomUUID();
    private static final String INTEGRATION_TYPE = "EVV_SANDATA";
    private static final String STATE_CODE = "MA";
    private static final String PAYER_TYPE = "MEDICAID";

    @BeforeEach
    void setUp() {
        service = new AgencyIntegrationConfigService(repo);
    }

    @Test
    void save_newConfig_noDuplicate_succeeds() {
        AgencyIntegrationConfig config = buildConfig(STATE_CODE, PAYER_TYPE);
        when(repo.findForUpdate(AGENCY_ID, INTEGRATION_TYPE, STATE_CODE, PAYER_TYPE))
                .thenReturn(Optional.empty());
        when(repo.save(config)).thenReturn(config);

        AgencyIntegrationConfig saved = service.save(config);

        assertThat(saved).isSameAs(config);
        verify(repo).save(config);
    }

    @Test
    void save_duplicateConfig_throws_DuplicateIntegrationConfigException() {
        AgencyIntegrationConfig config = buildConfig(STATE_CODE, PAYER_TYPE);
        AgencyIntegrationConfig existing = buildConfig(STATE_CODE, PAYER_TYPE);
        when(repo.findForUpdate(AGENCY_ID, INTEGRATION_TYPE, STATE_CODE, PAYER_TYPE))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.save(config))
                .isInstanceOf(DuplicateIntegrationConfigException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void save_nullStateCode_treatedAsDistinctKey() {
        AgencyIntegrationConfig config = buildConfig(null, PAYER_TYPE);
        when(repo.findForUpdate(AGENCY_ID, INTEGRATION_TYPE, null, PAYER_TYPE))
                .thenReturn(Optional.empty());
        when(repo.save(config)).thenReturn(config);

        AgencyIntegrationConfig saved = service.save(config);

        assertThat(saved).isSameAs(config);
        verify(repo).findForUpdate(AGENCY_ID, INTEGRATION_TYPE, null, PAYER_TYPE);
    }

    @Test
    void save_nullPayerType_treatedAsDistinctKey() {
        AgencyIntegrationConfig config = buildConfig(STATE_CODE, null);
        when(repo.findForUpdate(AGENCY_ID, INTEGRATION_TYPE, STATE_CODE, null))
                .thenReturn(Optional.empty());
        when(repo.save(config)).thenReturn(config);

        AgencyIntegrationConfig saved = service.save(config);

        assertThat(saved).isSameAs(config);
        verify(repo).findForUpdate(AGENCY_ID, INTEGRATION_TYPE, STATE_CODE, null);
    }

    @Test
    void findActive_delegatesCorrectly() {
        AgencyIntegrationConfig config = buildConfig(STATE_CODE, PAYER_TYPE);
        when(repo.findByAgencyIdAndIntegrationTypeAndActiveTrue(AGENCY_ID, INTEGRATION_TYPE))
                .thenReturn(Optional.of(config));

        Optional<AgencyIntegrationConfig> result = service.findActive(AGENCY_ID, INTEGRATION_TYPE);

        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(config);
    }

    private AgencyIntegrationConfig buildConfig(String stateCode, String payerType) {
        return new AgencyIntegrationConfig(
                AGENCY_ID,
                INTEGRATION_TYPE,
                "com.hcare.integration.evv.sandata.SandataSubmissionStrategy",
                stateCode,
                payerType,
                "https://api.sandata.com",
                "encrypted-creds",
                null,
                true);
    }
}
