package com.hcare.scheduling;

import com.hcare.AbstractIntegrationTest;
import com.hcare.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ShiftGenerationServiceIT extends AbstractIntegrationTest {

    @Autowired AgencyRepository agencyRepo;
    @Autowired ClientRepository clientRepo;
    @Autowired ServiceTypeRepository serviceTypeRepo;
    @Autowired RecurrencePatternRepository patternRepo;
    @Autowired ShiftRepository shiftRepo;
    @Autowired ShiftGenerationService shiftGenerationService;
    @Autowired ShiftGenerationScheduler shiftGenerationScheduler;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void generateForPattern_creates_correct_shifts_in_database() {
        Agency agency = agencyRepo.save(new Agency("Gen IT Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "GenIT", "Client", LocalDate.of(1960, 1, 1)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-GIT", true, "[]"));

        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(9, 0), 240, "[\"MONDAY\",\"FRIDAY\"]", nextMonday
        ));

        shiftGenerationService.generateForPattern(pattern);

        LocalDate horizon = LocalDate.now().plusWeeks(8);
        List<Shift> shifts = shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(), nextMonday.atStartOfDay(), horizon.plusDays(1).atStartOfDay()
        );

        assertThat(shifts).isNotEmpty();
        assertThat(shifts).allMatch(s ->
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.MONDAY ||
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.FRIDAY
        );
        assertThat(shifts).allMatch(s -> s.getSourcePatternId().equals(pattern.getId()));
        assertThat(shifts).allMatch(s -> s.getStatus() == ShiftStatus.OPEN);
        assertThat(shifts).allMatch(s -> s.getScheduledEnd().equals(s.getScheduledStart().plusMinutes(240)));
        assertThat(shifts).allMatch(s -> s.getScheduledStart().toLocalTime().equals(LocalTime.of(9, 0)));

        RecurrencePattern updated = patternRepo.findById(pattern.getId()).orElseThrow();
        assertThat(updated.getGeneratedThrough()).isEqualTo(horizon);
    }

    @Test
    void regenerateAfterEdit_deletes_future_open_shifts_and_regenerates() {
        Agency agency = agencyRepo.save(new Agency("Regen IT Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "RegenIT", "Client", LocalDate.of(1965, 4, 10)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-RIT", true, "[]"));

        LocalDate startDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(10, 0), 120, "[\"TUESDAY\"]", startDate
        ));

        // Initial generation
        shiftGenerationService.generateForPattern(pattern);
        int initialCount = shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(), LocalDateTime.now(), LocalDate.now().plusWeeks(9).atStartOfDay()
        ).size();
        assertThat(initialCount).isGreaterThan(0);

        // Regenerate (simulates a pattern edit — deletes old shifts and re-creates)
        shiftGenerationService.regenerateAfterEdit(pattern);

        List<Shift> afterRegen = shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(), LocalDateTime.now(), LocalDate.now().plusWeeks(9).atStartOfDay()
        );
        assertThat(afterRegen).isNotEmpty();
        assertThat(afterRegen).allMatch(s -> s.getSourcePatternId().equals(pattern.getId()));
        assertThat(afterRegen).allMatch(s -> s.getScheduledStart().getDayOfWeek() == DayOfWeek.TUESDAY);
    }

    @Test
    void scheduler_advanceGenerationFrontier_generates_shifts_for_patterns_behind_horizon() {
        Agency agency = agencyRepo.save(new Agency("Scheduler IT Agency", "TX"));
        Client client = clientRepo.save(new Client(agency.getId(), "SchedIT", "Client", LocalDate.of(1970, 7, 1)));
        ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-SCHED", true, "[]"));

        RecurrencePattern pattern = new RecurrencePattern(
            agency.getId(), client.getId(), st.getId(),
            LocalTime.of(8, 0), 180, "[\"WEDNESDAY\"]",
            LocalDate.now().minusDays(2)
        );
        // Set generatedThrough behind the horizon so the scheduler picks it up
        pattern.setGeneratedThrough(LocalDate.now().minusDays(1));
        patternRepo.save(pattern);

        shiftGenerationScheduler.advanceGenerationFrontier();

        List<Shift> shifts = shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(),
            LocalDateTime.now(),
            LocalDate.now().plusWeeks(9).atStartOfDay()
        );
        assertThat(shifts).isNotEmpty();
        assertThat(shifts).allMatch(s -> s.getScheduledStart().getDayOfWeek() == DayOfWeek.WEDNESDAY);

        RecurrencePattern updated = patternRepo.findById(pattern.getId()).orElseThrow();
        assertThat(updated.getGeneratedThrough())
            .isAfterOrEqualTo(LocalDate.now().plusWeeks(7));
    }
}
