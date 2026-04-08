package com.hcare.api.v1.dashboard.dto;

import java.util.List;

public record DashboardTodayResponse(
    int redEvvCount,
    int yellowEvvCount,
    int uncoveredCount,
    int onTrackCount,
    List<DashboardVisitRow> visits,
    List<DashboardAlert> alerts
) {}
