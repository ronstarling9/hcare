package com.hcare.api.v1.agencies.dto;

import com.hcare.domain.Agency;
import java.time.LocalDateTime;
import java.util.UUID;

public record AgencyResponse(UUID id, String name, String state, LocalDateTime createdAt) {
    public static AgencyResponse from(Agency a) {
        return new AgencyResponse(a.getId(), a.getName(), a.getState(), a.getCreatedAt());
    }
}
