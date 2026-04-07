package com.hcare.api.v1.clients.dto;

import jakarta.validation.constraints.Size;

public record UpdateMedicationRequest(
    @Size(min = 1) String name,
    String dosage,
    String route,
    String schedule,
    String prescriber
) {}
