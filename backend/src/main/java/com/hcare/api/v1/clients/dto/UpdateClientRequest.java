package com.hcare.api.v1.clients.dto;

import com.hcare.domain.ClientStatus;
import java.time.LocalDate;

public record UpdateClientRequest(
    String firstName,
    String lastName,
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
