package com.hcare.api.v1.clients.dto;

import com.hcare.domain.ClientStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record UpdateClientRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    LocalDate dateOfBirth,
    String address,
    String phone,
    String medicaidId,
    String serviceState,
    String preferredCaregiverGender,
    String preferredLanguages,
    Boolean noPetCaregiver,
    ClientStatus status
) {}
