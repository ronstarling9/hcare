package com.hcare.api.v1.scheduling;

import com.hcare.api.v1.scheduling.dto.CreateRecurrencePatternRequest;
import com.hcare.api.v1.scheduling.dto.RecurrencePatternResponse;
import com.hcare.api.v1.scheduling.dto.UpdateRecurrencePatternRequest;
import com.hcare.domain.RecurrencePattern;
import com.hcare.domain.RecurrencePatternRepository;
import com.hcare.domain.ShiftRepository;
import com.hcare.domain.ShiftStatus;
import com.hcare.scheduling.ShiftGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurrencePatternServiceTest {

    @Mock RecurrencePatternRepository patternRepository;
    @Mock ShiftRepository shiftRepository;
    @Mock ShiftGenerationService shiftGenerationService;

    RecurrencePatternService service;

    UUID agencyId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID serviceTypeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RecurrencePatternService(patternRepository, shiftRepository, shiftGenerationService);
    }

    // --- createPattern ---

    @Test
    void createPattern_saves_pattern_and_calls_generateForPattern() {
        CreateRecurrencePatternRequest req = new CreateRecurrencePatternRequest(
            clientId, null, serviceTypeId, null,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]",
            LocalDate.of(2026, 5, 4), null);

        RecurrencePattern saved = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.save(any())).thenReturn(saved);

        RecurrencePatternResponse result = service.createPattern(agencyId, req);

        assertThat(result.clientId()).isEqualTo(clientId);
        assertThat(result.scheduledStartTime()).isEqualTo(LocalTime.of(9, 0));
        verify(patternRepository).save(any(RecurrencePattern.class));
        verify(shiftGenerationService).generateForPattern(saved);
    }

    // --- listPatterns ---

    @Test
    void listPatterns_returns_all_patterns_for_agency() {
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findByAgencyId(agencyId)).thenReturn(List.of(pattern));

        List<RecurrencePatternResponse> result = service.listPatterns(agencyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).scheduledStartTime()).isEqualTo(LocalTime.of(9, 0));
    }

    // --- getPattern ---

    @Test
    void getPattern_returns_response_when_found() {
        UUID patternId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));

        RecurrencePatternResponse result = service.getPattern(agencyId, patternId);

        assertThat(result.scheduledDurationMinutes()).isEqualTo(120);
    }

    @Test
    void getPattern_throws_404_when_not_found() {
        UUID patternId = UUID.randomUUID();
        when(patternRepository.findById(patternId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPattern(agencyId, patternId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    @Test
    void getPattern_belonging_to_other_agency_throws_404() {
        UUID patternId = UUID.randomUUID();
        UUID otherAgencyId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(otherAgencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));

        assertThatThrownBy(() -> service.getPattern(agencyId, patternId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // --- updatePattern: scheduling fields trigger regeneration ---

    @Test
    void updatePattern_with_new_scheduledStartTime_calls_regenerateAfterEdit() {
        UUID patternId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(patternRepository.save(pattern)).thenReturn(pattern);

        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            LocalTime.of(14, 0), null, null, null, null, null);

        service.updatePattern(agencyId, patternId, req);

        verify(shiftGenerationService).regenerateAfterEdit(pattern);
        verifyNoMoreInteractions(shiftGenerationService);
    }

    @Test
    void updatePattern_with_new_scheduledDurationMinutes_calls_regenerateAfterEdit() {
        UUID patternId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(patternRepository.save(pattern)).thenReturn(pattern);

        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            null, 180, null, null, null, null);

        service.updatePattern(agencyId, patternId, req);

        verify(shiftGenerationService).regenerateAfterEdit(pattern);
    }

    @Test
    void updatePattern_with_new_daysOfWeek_calls_regenerateAfterEdit() {
        UUID patternId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(patternRepository.save(pattern)).thenReturn(pattern);

        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            null, null, "[\"WEDNESDAY\",\"FRIDAY\"]", null, null, null);

        service.updatePattern(agencyId, patternId, req);

        verify(shiftGenerationService).regenerateAfterEdit(pattern);
    }

    @Test
    void updatePattern_caregiverId_only_saves_in_place_without_regeneration() {
        UUID patternId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(patternRepository.save(pattern)).thenReturn(pattern);

        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            null, null, null, caregiverId, null, null);

        service.updatePattern(agencyId, patternId, req);

        verifyNoInteractions(shiftGenerationService);
        verify(patternRepository).save(pattern);
        assertThat(pattern.getCaregiverId()).isEqualTo(caregiverId);
    }

    @Test
    void updatePattern_endDate_only_saves_in_place_without_regeneration() {
        UUID patternId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(patternRepository.save(pattern)).thenReturn(pattern);

        LocalDate newEndDate = LocalDate.of(2026, 12, 31);
        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            null, null, null, null, null, newEndDate);

        service.updatePattern(agencyId, patternId, req);

        verifyNoInteractions(shiftGenerationService);
        assertThat(pattern.getEndDate()).isEqualTo(newEndDate);
    }

    @Test
    void updatePattern_with_all_null_fields_returns_early_without_save() {
        UUID patternId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));

        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            null, null, null, null, null, null);

        service.updatePattern(agencyId, patternId, req);

        verify(patternRepository, never()).save(any());
        verifyNoInteractions(shiftGenerationService);
    }

    // --- deactivatePattern ---

    @Test
    void deactivatePattern_sets_isActive_false_and_deletes_future_shifts() {
        UUID patternId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(patternRepository.save(pattern)).thenReturn(pattern);

        service.deactivatePattern(agencyId, patternId);

        assertThat(pattern.isActive()).isFalse();
        verify(shiftRepository).deleteUnstartedFutureShifts(
            eq(patternId), eq(agencyId), any(), eq(List.of(ShiftStatus.OPEN, ShiftStatus.ASSIGNED)));
        verify(patternRepository).save(pattern);
    }
}
