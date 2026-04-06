package com.hcare.api.v1.clients.dto;

public record UpdateMedicationRequest(
    String name,
    String dosage,
    String route,
    String schedule,
    String prescriber
) {}
