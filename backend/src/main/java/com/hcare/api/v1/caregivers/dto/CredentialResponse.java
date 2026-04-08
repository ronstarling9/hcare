package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.CaregiverCredential;
import com.hcare.domain.CredentialType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CredentialResponse(
    UUID id,
    UUID caregiverId,
    CredentialType credentialType,
    LocalDate issueDate,
    LocalDate expiryDate,
    boolean verified,
    UUID verifiedBy,
    LocalDateTime verifiedAt,
    LocalDateTime createdAt
) {
    public static CredentialResponse from(CaregiverCredential c) {
        return new CredentialResponse(
            c.getId(), c.getCaregiverId(), c.getCredentialType(),
            c.getIssueDate(), c.getExpiryDate(), c.isVerified(),
            c.getVerifiedBy(), c.getVerifiedAt(), c.getCreatedAt());
    }
}
