package com.hcare.evv;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

class EvvStateConfigIT extends AbstractIntegrationTest {

    @Autowired
    private EvvStateConfigRepository repository;

    @Test
    void allStatesAndDcAreSeeded() {
        long count = repository.count();
        assertThat(count).isEqualTo(51);
    }

    @Test
    void closedStates_haveClosed_systemModel() {
        List<String> closedStateCodes = List.of("MD", "SC", "OR", "KS", "SD");
        for (String code : closedStateCodes) {
            Optional<EvvStateConfig> config = repository.findByStateCode(code);
            assertThat(config).as("State %s should be seeded", code).isPresent();
            assertThat(config.get().getSystemModel())
                .as("State %s should be CLOSED", code)
                .isEqualTo(EvvSystemModel.CLOSED);
        }
    }

    @Test
    void nj_requiresRealTimeSubmission() {
        EvvStateConfig nj = repository.findByStateCode("NJ").orElseThrow();
        assertThat(nj.isRequiresRealTimeSubmission()).isTrue();
    }

    @Test
    void hi_hasManualEntryCapOf15Percent() {
        EvvStateConfig hi = repository.findByStateCode("HI").orElseThrow();
        assertThat(hi.getManualEntryCapPercent()).isEqualTo(15);
    }

    @Test
    void pa_hasComplianceThresholdOf85Percent() {
        EvvStateConfig pa = repository.findByStateCode("PA").orElseThrow();
        assertThat(pa.getComplianceThresholdPercent()).isEqualTo(85);
    }

    @Test
    void ok_doesNotSupportCoResidentExemption() {
        EvvStateConfig ok = repository.findByStateCode("OK").orElseThrow();
        assertThat(ok.isCoResidentExemptionSupported()).isFalse();
    }
}
