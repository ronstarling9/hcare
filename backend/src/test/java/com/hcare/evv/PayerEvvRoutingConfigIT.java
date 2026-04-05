package com.hcare.evv;

import com.hcare.AbstractIntegrationTest;
import com.hcare.domain.PayerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

class PayerEvvRoutingConfigIT extends AbstractIntegrationTest {

    @Autowired private PayerEvvRoutingConfigRepository routingRepo;

    @Test
    void routing_config_can_be_found_by_state_and_payer_type() {
        // NY uses HHAeXchange as default but some MCOs require Sandata — simulate that override.
        PayerEvvRoutingConfig config = new PayerEvvRoutingConfig("NY", PayerType.MEDICAID, AggregatorType.SANDATA);
        routingRepo.save(config);

        Optional<PayerEvvRoutingConfig> found = routingRepo.findByStateCodeAndPayerType("NY", PayerType.MEDICAID);
        assertThat(found).isPresent();
        assertThat(found.get().getAggregatorType()).isEqualTo(AggregatorType.SANDATA);
    }

    @Test
    void lookup_returns_empty_when_no_override_exists() {
        // CO uses Sandata by default and has no MCO routing overrides.
        Optional<PayerEvvRoutingConfig> found = routingRepo.findByStateCodeAndPayerType("CO", PayerType.MEDICAID);
        assertThat(found).isEmpty();
    }
}
