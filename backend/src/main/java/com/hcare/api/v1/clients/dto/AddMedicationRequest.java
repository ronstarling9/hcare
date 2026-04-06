package com.hcare.api.v1.clients.dto;

import jakarta.validation.constraints.NotBlank;

public record AddMedicationRequest(
    @NotBlank String name,
    String dosage,
    String route,
    String schedule,
    String prescriber
) {}
