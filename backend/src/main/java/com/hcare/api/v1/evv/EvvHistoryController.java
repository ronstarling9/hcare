package com.hcare.api.v1.evv;

import com.hcare.api.v1.evv.dto.EvvHistoryRow;
import com.hcare.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/evv")
public class EvvHistoryController {

    private final EvvHistoryService evvHistoryService;

    public EvvHistoryController(EvvHistoryService evvHistoryService) {
        this.evvHistoryService = evvHistoryService;
    }

    /**
     * Returns EVV compliance history for shifts whose scheduledStart falls within [start, end).
     *
     * <p>Example: GET /api/v1/evv/history?start=2026-04-01T00:00:00&end=2026-04-30T23:59:59
     *
     * @param start ISO-8601 LocalDateTime (no timezone suffix) — inclusive lower bound
     * @param end   ISO-8601 LocalDateTime (no timezone suffix) — exclusive upper bound
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Page<EvvHistoryRow>> getHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @PageableDefault(size = 50) Pageable pageable) { // default sort: scheduledStart DESC (applied in service)
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "end must be after start");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 366) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Date range must not exceed 366 days");
        }
        return ResponseEntity.ok(
            evvHistoryService.getHistory(principal.getAgencyId(), start, end, pageable));
    }
}
