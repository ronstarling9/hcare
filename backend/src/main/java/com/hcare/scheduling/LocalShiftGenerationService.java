package com.hcare.scheduling;

import com.hcare.domain.RecurrencePattern;
import com.hcare.domain.RecurrencePatternRepository;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftRepository;
import com.hcare.domain.ShiftStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LocalShiftGenerationService implements ShiftGenerationService {

    private static final Logger log = LoggerFactory.getLogger(LocalShiftGenerationService.class);

    static final int HORIZON_WEEKS = 8;

    private final RecurrencePatternRepository patternRepository;
    private final ShiftRepository shiftRepository;

    public LocalShiftGenerationService(RecurrencePatternRepository patternRepository,
                                        ShiftRepository shiftRepository) {
        this.patternRepository = patternRepository;
        this.shiftRepository = shiftRepository;
    }

    @Override
    @Transactional
    public void generateForPattern(RecurrencePattern pattern) {
        if (!pattern.isActive()) return;

        LocalDate start = pattern.getGeneratedThrough().plusDays(1);
        LocalDate horizonEnd = LocalDate.now().plusWeeks(HORIZON_WEEKS);
        LocalDate end = (pattern.getEndDate() != null && pattern.getEndDate().isBefore(horizonEnd))
            ? pattern.getEndDate()
            : horizonEnd;

        if (start.isAfter(end)) return;

        Set<DayOfWeek> daysOfWeek = parseDaysOfWeek(pattern.getDaysOfWeek());
        List<Shift> shifts = new ArrayList<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (daysOfWeek.contains(date.getDayOfWeek())) {
                LocalDateTime scheduledStart = date.atTime(pattern.getScheduledStartTime());
                LocalDateTime scheduledEnd = scheduledStart.plusMinutes(pattern.getScheduledDurationMinutes());
                shifts.add(new Shift(
                    pattern.getAgencyId(),
                    pattern.getId(),
                    pattern.getClientId(),
                    pattern.getCaregiverId(),
                    pattern.getServiceTypeId(),
                    pattern.getAuthorizationId(),
                    scheduledStart,
                    scheduledEnd
                ));
            }
        }

        if (!shifts.isEmpty()) {
            shiftRepository.saveAll(shifts);
        }
        pattern.setGeneratedThrough(end);
        patternRepository.save(pattern);
        log.debug("Generated {} shifts for pattern {} (agency {}), generatedThrough advanced to {}",
            shifts.size(), pattern.getId(), pattern.getAgencyId(), end);
    }

    @Override
    @Transactional
    public void regenerateAfterEdit(RecurrencePattern pattern) {
        shiftRepository.deleteUnstartedFutureShifts(
            pattern.getId(),
            pattern.getAgencyId(),
            LocalDateTime.now(),
            List.of(ShiftStatus.OPEN, ShiftStatus.ASSIGNED)
        );
        // Reset to today so generateForPattern starts from tomorrow — avoids creating
        // stale past-time shifts or a duplicate alongside an in-progress today's visit.
        pattern.setGeneratedThrough(LocalDate.now());
        generateForPattern(pattern);
    }

    /**
     * Parses a JSON TEXT array of DayOfWeek names e.g. ["MONDAY","WEDNESDAY","FRIDAY"].
     * Package-private for unit testing without reflection.
     */
    static Set<DayOfWeek> parseDaysOfWeek(String json) {
        if (json == null || json.isBlank()) return Set.of();
        try {
            return Arrays.stream(json.replaceAll("[\\[\\]\"\\s]", "").split(","))
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid daysOfWeek JSON — expected array of DayOfWeek names, got: " + json, e);
        }
    }
}
