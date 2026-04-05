package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CaregiverDomainIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void caregiver_can_be_saved_and_retrieved() {
        Agency agency = agencyRepo.save(new Agency("Caregiver Test Agency", "TX"));
        Caregiver caregiver = caregiverRepo.save(
            new Caregiver(agency.getId(), "Alice", "Smith", "alice@test.com"));

        Caregiver loaded = caregiverRepo.findById(caregiver.getId()).orElseThrow();
        assertThat(loaded.getFirstName()).isEqualTo("Alice");
        assertThat(loaded.getLastName()).isEqualTo("Smith");
        assertThat(loaded.getEmail()).isEqualTo("alice@test.com");
        assertThat(loaded.getStatus()).isEqualTo(CaregiverStatus.ACTIVE);
        assertThat(loaded.isHasPet()).isFalse();
        assertThat(loaded.getLanguages()).isEqualTo("[]");
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
    }

    @Test
    void caregiver_stores_geocoded_location() {
        Agency agency = agencyRepo.save(new Agency("Geo Agency", "TX"));
        Caregiver caregiver = new Caregiver(agency.getId(), "Bob", "Jones", "bob@geo.com");
        caregiver.setHomeLat(new BigDecimal("30.2672"));
        caregiver.setHomeLng(new BigDecimal("-97.7431"));
        caregiverRepo.save(caregiver);

        Caregiver loaded = caregiverRepo.findById(caregiver.getId()).orElseThrow();
        assertThat(loaded.getHomeLat()).isEqualByComparingTo(new BigDecimal("30.2672"));
        assertThat(loaded.getHomeLng()).isEqualByComparingTo(new BigDecimal("-97.7431"));
    }

    @Test
    void caregiver_agencyFilter_excludes_other_agency_caregivers() {
        Agency agencyA = agencyRepo.save(new Agency("Caregiver Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Caregiver Agency B", "CA"));

        Caregiver caregiverA = caregiverRepo.save(
            new Caregiver(agencyA.getId(), "Alice", "A", "alice-a@test.com"));
        caregiverRepo.save(
            new Caregiver(agencyB.getId(), "Bob", "B", "bob-b@test.com"));

        TenantContext.set(agencyA.getId());
        List<Caregiver> result;
        try {
            result = transactionTemplate.execute(status -> caregiverRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(Caregiver::getId).toList())
            .contains(caregiverA.getId());
        assertThat(result).allMatch(c -> c.getAgencyId().equals(agencyA.getId()));
    }
}
