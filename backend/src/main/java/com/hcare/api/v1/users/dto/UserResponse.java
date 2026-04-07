package com.hcare.api.v1.users.dto;

import com.hcare.domain.AgencyUser;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(UUID id, UUID agencyId, String email, String role, LocalDateTime createdAt) {
    public static UserResponse from(AgencyUser u) {
        return new UserResponse(u.getId(), u.getAgencyId(), u.getEmail(),
            u.getRole().name(), u.getCreatedAt());
    }
}
