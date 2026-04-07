package com.hcare.api.v1.clients.dto;

import com.hcare.domain.FamilyPortalUser;
import java.time.LocalDateTime;
import java.util.UUID;

public record FamilyPortalUserResponse(
    UUID id,
    UUID clientId,
    String name,
    String email,
    LocalDateTime lastLoginAt,
    LocalDateTime createdAt
) {
    public static FamilyPortalUserResponse from(FamilyPortalUser f) {
        return new FamilyPortalUserResponse(
            f.getId(), f.getClientId(), f.getName(), f.getEmail(),
            f.getLastLoginAt(), f.getCreatedAt());
    }
}
