package com.hcare.scheduling;

import com.hcare.domain.RecurrencePattern;

public interface ShiftGenerationService {

    /**
     * Generates shifts for the pattern from its current generatedThrough frontier through
     * LocalDate.now() plus 8 weeks. Safe to call multiple times — only advances the frontier.
     * No-ops silently for inactive patterns (isActive = false).
     */
    void generateForPattern(RecurrencePattern pattern);

    /**
     * Deletes future unstarted shifts (OPEN or ASSIGNED, scheduledStart after now) for the
     * given pattern, resets generatedThrough to today, then calls generateForPattern.
     * Generation resumes from tomorrow — preserving any in-progress visit on today's date.
     * Called when a pattern's scheduling fields are edited.
     * Note: no-ops silently on inactive patterns (delegates to generateForPattern).
     */
    void regenerateAfterEdit(RecurrencePattern pattern);
}
