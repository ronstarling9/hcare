package com.hcare.api.v1.clients.dto;

import com.hcare.domain.Authorization;
import com.hcare.domain.UnitType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuthorizationResponse(
    UUID id,
    UUID clientId,
    UUID payerId,
    UUID serviceTypeId,
    UUID agencyId,
    String authNumber,
    BigDecimal authorizedUnits,
    BigDecimal usedUnits,
    UnitType unitType,
    LocalDate startDate,
    LocalDate endDate,
    Long version,
    LocalDateTime createdAt
) {
    public static AuthorizationResponse from(Authorization a) {
        return new AuthorizationResponse(
            a.getId(), a.getClientId(), a.getPayerId(), a.getServiceTypeId(),
            a.getAgencyId(), a.getAuthNumber(), a.getAuthorizedUnits(), a.getUsedUnits(),
            a.getUnitType(), a.getStartDate(), a.getEndDate(), a.getVersion(), a.getCreatedAt());
    }
}
