package com.hcare.scheduling;

import com.hcare.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftGenerationServiceTest {

    @Mock RecurrencePatternRepository patternRepo;
    @Mock ShiftRepository shiftRepo;
    @InjectMocks LocalShiftGenerationService service;

    private RecurrencePattern buildPattern(String daysOfWeek, LocalDate generatedThrough) {
        RecurrencePattern pattern = new RecurrencePattern(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            LocalTime.of(9, 0), 240, daysOfWeek,
            LocalDate.now().minusDays(1)
        );
        pattern.setGeneratedThrough(generatedThrough);
        return pattern;
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateForPattern_creates_shifts_only_on_matching_days() {
        RecurrencePattern pattern = buildPattern("[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]",
            LocalDate.now().minusDays(1));

        service.generateForPattern(pattern);

        ArgumentCaptor<List<Shift>> captor = ArgumentCaptor.forClass(List.class);
        verify(shiftRepo).saveAll(captor.capture());
        List<Shift> saved = captor.getValue();

        assertThat(saved).isNotEmpty();
        assertThat(saved).allMatch(s ->
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.MONDAY ||
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.WEDNESDAY ||
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.FRIDAY
        );
        assertThat(saved).noneMatch(s ->
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.TUESDAY ||
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.THURSDAY ||
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.SATURDAY ||
            s.getScheduledStart().getDayOfWeek() == DayOfWeek.SUNDAY
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateForPattern_shift_times_match_pattern_time_and_duration() {
        RecurrencePattern pattern = buildPattern("[\"TUESDAY\"]", LocalDate.now().minusDays(1));

        service.generateForPattern(pattern);

        ArgumentCaptor<List<Shift>> captor = ArgumentCaptor.forClass(List.class);
        verify(shiftRepo).saveAll(captor.capture());
        captor.getValue().forEach(s -> {
            assertThat(s.getScheduledStart().toLocalTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(s.getScheduledEnd()).isEqualTo(s.getScheduledStart().plusMinutes(240));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateForPattern_respects_pattern_endDate() {
        LocalDate endDate = LocalDate.now().plusWeeks(2);
        RecurrencePattern pattern = buildPattern("[\"MONDAY\"]", LocalDate.now().minusDays(1));
        pattern.setEndDate(endDate);

        service.generateForPattern(pattern);

        ArgumentCaptor<List<Shift>> captor = ArgumentCaptor.forClass(List.class);
        verify(shiftRepo).saveAll(captor.capture());
        captor.getValue().forEach(s ->
            assertThat(s.getScheduledStart().toLocalDate()).isBeforeOrEqualTo(endDate)
        );
        assertThat(pattern.getGeneratedThrough()).isEqualTo(endDate);
    }

    @Test
    void generateForPattern_does_nothing_for_inactive_pattern() {
        RecurrencePattern pattern = buildPattern("[\"MONDAY\"]", LocalDate.now().minusDays(1));
        pattern.setActive(false);

        service.generateForPattern(pattern);

        verifyNoInteractions(shiftRepo, patternRepo);
    }

    @Test
    void generateForPattern_does_nothing_when_already_at_horizon() {
        // generatedThrough = now + 8 weeks → start would be after end → early return
        RecurrencePattern pattern = buildPattern("[\"MONDAY\"]", LocalDate.now().plusWeeks(8));

        service.generateForPattern(pattern);

        verify(shiftRepo, never()).saveAll(any());
        verify(patternRepo, never()).save(any());
    }

    @Test
    void generateForPattern_updates_generatedThrough_and_saves_pattern() {
        RecurrencePattern pattern = buildPattern("[\"TUESDAY\"]", LocalDate.now().minusDays(1));

        service.generateForPattern(pattern);

        assertThat(pattern.getGeneratedThrough()).isEqualTo(LocalDate.now().plusWeeks(8));
        verify(patternRepo).save(pattern);
    }

    @Test
    void regenerateAfterEdit_deletes_future_unstarted_shifts_then_regenerates() {
        RecurrencePattern pattern = buildPattern("[\"WEDNESDAY\"]", LocalDate.now().minusDays(1));

        service.regenerateAfterEdit(pattern);

        verify(shiftRepo).deleteUnstartedFutureShifts(
            eq(pattern.getId()),
            eq(pattern.getAgencyId()),
            any(LocalDateTime.class),
            eq(List.of(ShiftStatus.OPEN, ShiftStatus.ASSIGNED))
        );
        // generateForPattern is called internally: pattern saved + shifts generated
        verify(patternRepo).save(pattern);
    }

    @Test
    void regenerateAfterEdit_resets_generatedThrough_to_today_so_generation_starts_tomorrow() {
        // Ensures no stale past-time shift is created for today's matching day of week,
        // and no duplicate is created alongside an in-progress today's shift.
        RecurrencePattern pattern = buildPattern("[\"WEDNESDAY\"]", LocalDate.now().minusDays(1));

        service.regenerateAfterEdit(pattern);

        ArgumentCaptor<RecurrencePattern> captor = ArgumentCaptor.forClass(RecurrencePattern.class);
        verify(patternRepo).save(captor.capture());
        // generatedThrough must be >= today (generation starts from tomorrow at earliest)
        assertThat(captor.getValue().getGeneratedThrough())
            .isAfterOrEqualTo(LocalDate.now());
    }

    @Test
    void parseDaysOfWeek_parses_multiple_days() {
        Set<DayOfWeek> result = LocalShiftGenerationService.parseDaysOfWeek(
            "[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]"
        );
        assertThat(result).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void parseDaysOfWeek_parses_single_day() {
        Set<DayOfWeek> result = LocalShiftGenerationService.parseDaysOfWeek("[\"SATURDAY\"]");
        assertThat(result).containsExactly(DayOfWeek.SATURDAY);
    }

    @Test
    void generateForPattern_advances_frontier_even_when_no_shifts_generated() {
        // Pattern is active and window is non-empty, but no days in the window match.
        // Set generatedThrough to yesterday so the window [today, today+56d] is open.
        // Use a single day-of-week that falls OUTSIDE today's date so no shift is created
        // in the very first few days. Since we can't control the calendar in a unit test,
        // verify the pattern is saved (frontier advanced) regardless of whether saveAll fires.
        RecurrencePattern pattern = buildPattern("[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"]",
            LocalDate.now().minusDays(1));
        // Override: use a window with end < start to get an empty window — but that triggers early return.
        // Instead, use a zero-day window by setting generatedThrough to 56 days from now minus 1 day.
        // The easiest approach: set generatedThrough to now+55d so start=now+56d, end=now+56d (1-day window).
        // That 1-day window will include exactly 1 matching day (all days are in daysOfWeek), so saveAll IS called.
        //
        // The real scenario to test: shifts list is empty BUT frontier is still advanced.
        // Use a pattern whose daysOfWeek is empty (after L1 fix, parseDaysOfWeek returns empty set for blank).
        // With empty daysOfWeek, the loop runs but never adds a shift. Pattern should still be saved.
        RecurrencePattern emptyDayPattern = buildPattern("[]", LocalDate.now().minusDays(1));

        service.generateForPattern(emptyDayPattern);

        // No shifts should be saved (empty daysOfWeek)
        verify(shiftRepo, never()).saveAll(any());
        // But the frontier MUST still be advanced — pattern is saved
        verify(patternRepo).save(emptyDayPattern);
        assertThat(emptyDayPattern.getGeneratedThrough()).isEqualTo(LocalDate.now().plusWeeks(8));
    }
}
