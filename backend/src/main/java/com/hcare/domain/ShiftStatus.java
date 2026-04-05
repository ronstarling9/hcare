package com.hcare.domain;

public enum ShiftStatus {
    OPEN,        // no caregiver assigned
    ASSIGNED,    // caregiver assigned, not yet started
    IN_PROGRESS, // caregiver clocked in
    COMPLETED,   // caregiver clocked out
    CANCELLED,
    MISSED       // scheduled time passed without clock-in
}
