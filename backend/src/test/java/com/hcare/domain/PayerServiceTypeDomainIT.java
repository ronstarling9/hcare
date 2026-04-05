package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PayerServiceTypeDomainIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private PayerRepository payerRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private FeatureFlagsRepository featureFlagsRepo;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void payer_can_be_saved_and_retrieved() {
        Agency agency = agencyRepo.save(new Agency("Payer Test Agency", "TX"));
        Payer payer = payerRepo.save(
            new Payer(agency.getId(), "Texas Medicaid", PayerType.MEDICAID, "TX"));

        Payer loaded = payerRepo.findById(payer.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Texas Medicaid");
        assertThat(loaded.getPayerType()).isEqualTo(PayerType.MEDICAID);
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
        assertThat(loaded.getState()).isEqualTo("TX");
    }

    @Test
    void payer_agencyFilter_excludes_other_agency_payers() {
        Agency agencyA = agencyRepo.save(new Agency("Payer Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Payer Agency B", "CA"));

        Payer payerA = payerRepo.save(
            new Payer(agencyA.getId(), "Medicaid A", PayerType.MEDICAID, "TX"));
        payerRepo.save(
            new Payer(agencyB.getId(), "Medicaid B", PayerType.MEDICAID, "CA"));

        TenantContext.set(agencyA.getId());
        List<Payer> result;
        try {
            result = transactionTemplate.execute(status -> payerRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(Payer::getId).toList())
            .contains(payerA.getId());
        assertThat(result).allMatch(p -> p.getAgencyId().equals(agencyA.getId()));
    }

    @Test
    void serviceType_can_be_saved_with_required_credentials() {
        Agency agency = agencyRepo.save(new Agency("ST Test Agency", "TX"));
        ServiceType st = serviceTypeRepo.save(new ServiceType(
            agency.getId(), "Personal Care Services", "PCS", true, "[\"HHA\",\"CPR\"]"));

        ServiceType loaded = serviceTypeRepo.findById(st.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Personal Care Services");
        assertThat(loaded.isRequiresEvv()).isTrue();
        assertThat(loaded.getRequiredCredentials()).isEqualTo("[\"HHA\",\"CPR\"]");
    }

    @Test
    void featureFlags_default_values_are_correct() {
        Agency agency = agencyRepo.save(new Agency("Flags Test Agency", "TX"));
        FeatureFlags flags = featureFlagsRepo.save(new FeatureFlags(agency.getId()));

        FeatureFlags loaded = featureFlagsRepo.findById(flags.getId()).orElseThrow();
        assertThat(loaded.isAiSchedulingEnabled()).isFalse();
        assertThat(loaded.isFamilyPortalEnabled()).isTrue();
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
    }

    @Test
    void featureFlags_aiScheduling_can_be_enabled() {
        Agency agency = agencyRepo.save(new Agency("Pro Agency", "TX"));
        FeatureFlags flags = featureFlagsRepo.save(new FeatureFlags(agency.getId()));

        flags.setAiSchedulingEnabled(true);
        featureFlagsRepo.save(flags);

        FeatureFlags loaded = featureFlagsRepo.findById(flags.getId()).orElseThrow();
        assertThat(loaded.isAiSchedulingEnabled()).isTrue();
    }
}
