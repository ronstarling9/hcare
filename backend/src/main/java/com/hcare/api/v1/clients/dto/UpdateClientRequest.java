package com.hcare.api.v1.clients.dto;

import com.hcare.domain.ClientStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateClientRequest(
    @Size(min = 1) String firstName,
    @Size(min = 1) String lastName,
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
