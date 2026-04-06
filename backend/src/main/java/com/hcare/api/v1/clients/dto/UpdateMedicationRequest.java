package com.hcare.api.v1.clients.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateMedicationRequest(
    String name,
    String dosage,
    String route,
    String schedule,
    String prescriber
) {}
