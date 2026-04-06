package com.hcare.api.v1.scheduling;

import com.hcare.api.v1.scheduling.dto.CreateRecurrencePatternRequest;
import com.hcare.api.v1.scheduling.dto.RecurrencePatternResponse;
import com.hcare.api.v1.scheduling.dto.UpdateRecurrencePatternRequest;
import com.hcare.domain.RecurrencePattern;
import com.hcare.domain.RecurrencePatternRepository;
import com.hcare.domain.ShiftRepository;
import com.hcare.domain.ShiftStatus;
import com.hcare.scheduling.ShiftGenerationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class RecurrencePatternService {

    private final RecurrencePatternRepository patternRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftGenerationService shiftGenerationService;

    public RecurrencePatternService(RecurrencePatternRepository patternRepository,
                                     ShiftRepository shiftRepository,
                                     ShiftGenerationService shiftGenerationService) {
        this.patternRepository = patternRepository;
        this.shiftRepository = shiftRepository;
        this.shiftGenerationService = shiftGenerationService;
    }

    @Transactional
    public RecurrencePatternResponse createPattern(UUID agencyId, CreateRecurrencePatternRequest req) {
        RecurrencePattern pattern = new RecurrencePattern(
            agencyId, req.clientId(), req.serviceTypeId(),
            req.scheduledStartTime(), req.scheduledDurationMinutes(),
            req.daysOfWeek(), req.startDate());
        if (req.caregiverId() != null) pattern.setCaregiverId(req.caregiverId());
        if (req.authorizationId() != null) pattern.setAuthorizationId(req.authorizationId());
        if (req.endDate() != null) pattern.setEndDate(req.endDate());
        RecurrencePattern saved = patternRepository.save(pattern);
        shiftGenerationService.generateForPattern(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RecurrencePatternResponse> listPatterns(UUID agencyId) {
        return patternRepository.findByAgencyId(agencyId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public RecurrencePatternResponse getPattern(UUID patternId) {
        return toResponse(requirePattern(patternId));
    }

    @Transactional
    public RecurrencePatternResponse updatePattern(UUID patternId, UpdateRecurrencePatternRequest req) {
        RecurrencePattern pattern = requirePattern(patternId);

        boolean needsRegeneration = req.scheduledStartTime() != null
            || req.scheduledDurationMinutes() != null
            || req.daysOfWeek() != null;

        if (!needsRegeneration && req.caregiverId() == null
                && req.authorizationId() == null && req.endDate() == null) {
            return toResponse(pattern);
        }

        if (req.scheduledStartTime() != null) pattern.setScheduledStartTime(req.scheduledStartTime());
        if (req.scheduledDurationMinutes() != null) pattern.setScheduledDurationMinutes(req.scheduledDurationMinutes());
        if (req.daysOfWeek() != null) pattern.setDaysOfWeek(req.daysOfWeek());
        if (req.caregiverId() != null) pattern.setCaregiverId(req.caregiverId());
        if (req.authorizationId() != null) pattern.setAuthorizationId(req.authorizationId());
        if (req.endDate() != null) pattern.setEndDate(req.endDate());

        patternRepository.save(pattern);

        if (needsRegeneration) {
            shiftGenerationService.regenerateAfterEdit(pattern);
        }

        return toResponse(pattern);
    }

    @Transactional
    public void deactivatePattern(UUID patternId) {
        RecurrencePattern pattern = requirePattern(patternId);
        pattern.setActive(false);
        shiftRepository.deleteUnstartedFutureShifts(
            patternId, pattern.getAgencyId(),
            LocalDateTime.now(ZoneOffset.UTC),
            List.of(ShiftStatus.OPEN, ShiftStatus.ASSIGNED));
        patternRepository.save(pattern);
    }

    // --- helpers ---

    private RecurrencePattern requirePattern(UUID patternId) {
        return patternRepository.findById(patternId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "RecurrencePattern not found"));
    }

    private RecurrencePatternResponse toResponse(RecurrencePattern p) {
        return new RecurrencePatternResponse(
            p.getId(), p.getAgencyId(), p.getClientId(), p.getCaregiverId(),
            p.getServiceTypeId(), p.getAuthorizationId(),
            p.getScheduledStartTime(), p.getScheduledDurationMinutes(), p.getDaysOfWeek(),
            p.getStartDate(), p.getEndDate(), p.getGeneratedThrough(),
            p.isActive(), p.getVersion(), p.getCreatedAt());
    }
}
