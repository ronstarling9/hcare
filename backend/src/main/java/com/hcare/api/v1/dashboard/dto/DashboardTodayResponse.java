package com.hcare.api.v1.dashboard.dto;

import java.util.List;

/**
 * Top-level response for GET /api/v1/dashboard/today.
 */
public record DashboardTodayResponse(
    int totalVisitsToday,
    int completedVisits,
    int inProgressVisits,
    int openOrAssignedVisits,
    int redEvvCount,
    List<DashboardVisitRow> visits,
    List<DashboardAlert> alerts
) {}
