package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ShiftDomainIT extends AbstractIntegrationTest {

    @Autowired AgencyRepository agencyRepo;
    @Autowired ClientRepository clientRepo;
    @Autowired CaregiverRepository caregiverRepo;
    @Autowired ServiceTypeRepository serviceTypeRepo;
    @Autowired RecurrencePatternRepository patternRepo;
    @Autowired ShiftRepository shiftRepo;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void shift_status_is_open_when_no_caregiver() {
        Agency agency = agencyRepo.save(new Agency("Shift Open Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Open", "Client", LocalDate.of(1955, 3, 1)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-SO", true, "[]"));

        LocalDateTime start = LocalDate.of(2026, 4, 14).atTime(9, 0);
        Shift shift = shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), null,
            st.getId(), null, start, start.plusMinutes(240)
        ));

        Shift loaded = shiftRepo.findById(shift.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(ShiftStatus.OPEN);
        assertThat(loaded.getCaregiverId()).isNull();
        assertThat(loaded.getSourcePatternId()).isNull();
        assertThat(loaded.getScheduledEnd()).isEqualTo(start.plusMinutes(240));
    }

    @Test
    void shift_status_is_assigned_when_caregiver_provided() {
        Agency agency = agencyRepo.save(new Agency("Shift Assigned Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Assigned", "Client", LocalDate.of(1960, 7, 4)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-SA", true, "[]"));
        Caregiver cg = caregiverRepo.save(new Caregiver(agency.getId(), "Alice", "Smith", "alice.shift@test.com"));

        LocalDateTime start = LocalDate.of(2026, 4, 15).atTime(14, 0);
        Shift shift = shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), cg.getId(),
            st.getId(), null, start, start.plusHours(4)
        ));

        assertThat(shiftRepo.findById(shift.getId()).orElseThrow().getStatus())
            .isEqualTo(ShiftStatus.ASSIGNED);
    }

    @Test
    void shift_links_to_recurrence_pattern() {
        Agency agency = agencyRepo.save(new Agency("Shift Pattern Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Pattern", "Link", LocalDate.of(1970, 11, 11)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-SP", true, "[]"));

        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(10, 0), 180, "[\"THURSDAY\"]", LocalDate.of(2026, 4, 8)
        ));

        LocalDateTime start = LocalDate.of(2026, 4, 9).atTime(10, 0);
        Shift shift = shiftRepo.save(new Shift(
            agency.getId(), pattern.getId(), client.getId(), null,
            st.getId(), null, start, start.plusMinutes(180)
        ));

        assertThat(shiftRepo.findById(shift.getId()).orElseThrow().getSourcePatternId())
            .isEqualTo(pattern.getId());
    }

    @Test
    void shift_agencyFilter_excludes_other_agency_shifts() {
        Agency agencyA = agencyRepo.save(new Agency("Shift Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Shift Agency B", "CA"));
        Client clientA = clientRepo.save(new Client(agencyA.getId(), "A", "Shift", LocalDate.of(1960, 1, 1)));
        Client clientB = clientRepo.save(new Client(agencyB.getId(), "B", "Shift", LocalDate.of(1960, 1, 1)));
        ServiceType stA = serviceTypeRepo.save(new ServiceType(agencyA.getId(), "PCS", "PCS-SA2", true, "[]"));
        ServiceType stB = serviceTypeRepo.save(new ServiceType(agencyB.getId(), "PCS", "PCS-SB2", true, "[]"));

        LocalDateTime start = LocalDate.of(2026, 4, 14).atTime(9, 0);
        Shift shiftA = shiftRepo.save(new Shift(
            agencyA.getId(), null, clientA.getId(), null, stA.getId(), null, start, start.plusHours(4)
        ));
        shiftRepo.save(new Shift(
            agencyB.getId(), null, clientB.getId(), null, stB.getId(), null, start, start.plusHours(4)
        ));

        TenantContext.set(agencyA.getId());
        List<Shift> result;
        try {
            result = transactionTemplate.execute(status -> shiftRepo.findAll());
        } finally {
            TenantContext.clear();
        }

        assertThat(result).isNotNull();
        assertThat(result.stream().map(Shift::getId).toList()).contains(shiftA.getId());
        assertThat(result).allMatch(s -> s.getAgencyId().equals(agencyA.getId()));
    }

    @Test
    void findByClientIdAndScheduledStartBetween_returns_matching_shifts() {
        Agency agency = agencyRepo.save(new Agency("Shift Window Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "Window", "Client", LocalDate.of(1965, 4, 20)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-SW", true, "[]"));

        LocalDateTime inWindow = LocalDate.of(2026, 4, 20).atTime(9, 0);
        LocalDateTime outOfWindow = LocalDate.of(2026, 5, 20).atTime(9, 0);

        shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null, st.getId(), null,
            inWindow, inWindow.plusHours(4)));
        shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null, st.getId(), null,
            outOfWindow, outOfWindow.plusHours(4)));

        List<Shift> result = shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(),
            LocalDate.of(2026, 4, 1).atStartOfDay(),
            LocalDate.of(2026, 4, 30).atTime(23, 59)
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScheduledStart()).isEqualTo(inWindow);
    }

    @Test
    void deleteUnstartedFutureShifts_leaves_completed_and_other_agency_shifts_intact() {
        Agency agencyA = agencyRepo.save(new Agency("Delete Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Delete Agency B", "CA"));
        Client clientA = clientRepo.save(new Client(agencyA.getId(), "Del", "A", LocalDate.of(1960, 1, 1)));
        Client clientB = clientRepo.save(new Client(agencyB.getId(), "Del", "B", LocalDate.of(1960, 1, 1)));
        ServiceType stA = serviceTypeRepo.save(new ServiceType(agencyA.getId(), "PCS", "PCS-DEL-A", true, "[]"));
        ServiceType stB = serviceTypeRepo.save(new ServiceType(agencyB.getId(), "PCS", "PCS-DEL-B", true, "[]"));

        UUID patternId = UUID.randomUUID();
        LocalDateTime future = LocalDateTime.now().plusDays(7);

        // Should be deleted: agencyA OPEN shift in future
        Shift toDelete = shiftRepo.save(new Shift(agencyA.getId(), patternId, clientA.getId(), null,
            stA.getId(), null, future, future.plusHours(4)));

        // Should survive: COMPLETED shift for same pattern
        Shift completed = shiftRepo.save(new Shift(agencyA.getId(), patternId, clientA.getId(), null,
            stA.getId(), null, future.plusDays(1), future.plusDays(1).plusHours(4)));
        completed.setStatus(ShiftStatus.COMPLETED);
        shiftRepo.save(completed);

        // Should survive: agencyB's OPEN shift for the same patternId (different agency)
        Shift otherAgency = shiftRepo.save(new Shift(agencyB.getId(), patternId, clientB.getId(), null,
            stB.getId(), null, future, future.plusHours(4)));

        shiftRepo.deleteUnstartedFutureShifts(
            patternId, agencyA.getId(), LocalDateTime.now(),
            List.of(ShiftStatus.OPEN, ShiftStatus.ASSIGNED)
        );

        assertThat(shiftRepo.findById(toDelete.getId())).isEmpty();
        assertThat(shiftRepo.findById(completed.getId())).isPresent();
        assertThat(shiftRepo.findById(otherAgency.getId())).isPresent();
    }
}
