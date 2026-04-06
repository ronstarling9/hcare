package com.hcare.api.v1.scheduling.dto;

final class SchedulingValidation {
    static final String DAYS_OF_WEEK_REGEXP =
        "^\\[\"(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\"" +
        "(,\"(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\")*\\]$";
    static final String DAYS_OF_WEEK_MESSAGE =
        "daysOfWeek must be a non-empty JSON array of uppercase day names, " +
        "e.g. [\"MONDAY\",\"WEDNESDAY\"]";
    private SchedulingValidation() {}
}
