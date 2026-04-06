package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.CredentialType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AddCredentialRequest(
    @NotNull CredentialType credentialType,
    LocalDate issueDate,
    @NotNull LocalDate expiryDate
) {}
