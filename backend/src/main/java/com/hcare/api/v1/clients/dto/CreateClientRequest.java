package com.hcare.api.v1.clients.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateClientRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull LocalDate dateOfBirth,
    String address,
    String phone,
    String medicaidId,
    String serviceState,
    String preferredCaregiverGender,
    String preferredLanguages,
    Boolean noPetCaregiver
) {}
