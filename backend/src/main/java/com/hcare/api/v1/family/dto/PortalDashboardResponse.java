package com.hcare.api.v1.family.dto;

public record PortalDashboardResponse(
    String clientFirstName,
    String agencyTimezone,
    TodayVisitDto todayVisit,          // null if no shift today
    java.util.List<UpcomingVisitDto> upcomingVisits,
    LastVisitDto lastVisit             // null if no completed visits ever
) {
    public record TodayVisitDto(
        String shiftId,
        String scheduledStart,
        String scheduledEnd,
        String status,        // "GREY" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED"
        String clockedInAt,   // null unless IN_PROGRESS or COMPLETED
        String clockedOutAt,  // null unless COMPLETED
        CaregiverDto caregiver  // null when status is CANCELLED
    ) {}

    public record CaregiverDto(
        String name,
        String serviceType
    ) {}

    public record UpcomingVisitDto(
        String scheduledStart,
        String scheduledEnd,
        String caregiverName
    ) {}

    public record LastVisitDto(
        String date,          // "YYYY-MM-DD"
        String clockedOutAt,
        int durationMinutes,
        String noteText       // null if caregiver entered no notes
    ) {}
}
