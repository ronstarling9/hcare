package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RecurrencePatternDomainIT extends AbstractIntegrationTest {

    @Autowired AgencyRepository agencyRepo;
    @Autowired ClientRepository clientRepo;
    @Autowired ServiceTypeRepository serviceTypeRepo;
    @Autowired RecurrencePatternRepository patternRepo;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void recurrencePattern_can_be_saved_and_retrieved() {
        Agency agency = agencyRepo.save(new Agency("RP Save Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Jane", "Doe", LocalDate.of(1960, 1, 15)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "Personal Care", "PCS-RP1", true, "[]"));

        RecurrencePattern pattern = new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(9, 0), 240, "[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]",
            LocalDate.of(2026, 4, 7)
        );
        patternRepo.save(pattern);

        RecurrencePattern loaded = patternRepo.findById(pattern.getId()).orElseThrow();
        assertThat(loaded.getAgencyId()).isEqualTo(agency.getId());
        assertThat(loaded.getClientId()).isEqualTo(client.getId());
        assertThat(loaded.getServiceTypeId()).isEqualTo(st.getId());
        assertThat(loaded.getScheduledStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(loaded.getScheduledDurationMinutes()).isEqualTo(240);
        assertThat(loaded.getDaysOfWeek()).isEqualTo("[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]");
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.getVersion()).isZero();
        // generatedThrough is initialized to startDate - 1 day
        assertThat(loaded.getGeneratedThrough()).isEqualTo(LocalDate.of(2026, 4, 6));
        assertThat(loaded.getCaregiverId()).isNull();
        assertThat(loaded.getEndDate()).isNull();
    }

    @Test
    void recurrencePattern_version_increments_on_update() {
        Agency agency = agencyRepo.save(new Agency("RP Version Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Ver", "Test", LocalDate.of(1970, 6, 1)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "SN", "SN-RP2", true, "[]"));

        RecurrencePattern pattern = new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(8, 0), 480, "[\"TUESDAY\"]", LocalDate.of(2026, 4, 8)
        );
        patternRepo.save(pattern);
        assertThat(pattern.getVersion()).isZero();

        pattern.setActive(false);
        patternRepo.save(pattern);

        assertThat(patternRepo.findById(pattern.getId()).orElseThrow().getVersion()).isEqualTo(1L);
    }

    @Test
    void recurrencePattern_agencyFilter_excludes_other_agency_patterns() {
        Agency agencyA = agencyRepo.save(new Agency("RP Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("RP Agency B", "CA"));
        Client clientA = clientRepo.save(new Client(agencyA.getId(), "A", "Client", LocalDate.of(1960, 1, 1)));
        Client clientB = clientRepo.save(new Client(agencyB.getId(), "B", "Client", LocalDate.of(1960, 1, 1)));
        ServiceType stA = serviceTypeRepo.save(new ServiceType(agencyA.getId(), "PCS", "PCS-RPA", true, "[]"));
        ServiceType stB = serviceTypeRepo.save(new ServiceType(agencyB.getId(), "PCS", "PCS-RPB", true, "[]"));

        RecurrencePattern patternA = patternRepo.save(new RecurrencePattern(
            agencyA.getId(), clientA.getId(), stA.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 4, 7)
        ));
        patternRepo.save(new RecurrencePattern(
            agencyB.getId(), clientB.getId(), stB.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 4, 7)
        ));

        TenantContext.set(agencyA.getId());
        List<RecurrencePattern> result;
        try {
            result = transactionTemplate.execute(status -> patternRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(RecurrencePattern::getId).toList())
            .contains(patternA.getId());
        assertThat(result).allMatch(p -> p.getAgencyId().equals(agencyA.getId()));
    }

    @Test
    void findActivePatternsBehindHorizon_returns_only_patterns_behind_horizon() {
        Agency agency = agencyRepo.save(new Agency("RP Horizon Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Horizon", "Client", LocalDate.of(1965, 3, 10)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-RPH", true, "[]"));

        // Pattern behind horizon: generatedThrough = yesterday
        RecurrencePattern behindHorizon = new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(10, 0), 60, "[\"WEDNESDAY\"]", LocalDate.now().minusDays(2)
        );
        patternRepo.save(behindHorizon);

        // Pattern at horizon: generatedThrough manually set to now + 8 weeks
        RecurrencePattern atHorizon = new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(11, 0), 60, "[\"THURSDAY\"]", LocalDate.now().minusDays(2)
        );
        atHorizon.setGeneratedThrough(LocalDate.now().plusWeeks(8));
        patternRepo.save(atHorizon);

        LocalDate horizon = LocalDate.now().plusWeeks(8);
        List<RecurrencePattern> result = patternRepo.findActivePatternsBehindHorizon(horizon, LocalDate.now());

        assertThat(result.stream().map(RecurrencePattern::getId).toList())
            .contains(behindHorizon.getId())
            .doesNotContain(atHorizon.getId());
    }
}
