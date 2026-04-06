package com.hcare.api.v1.clients.dto;

import java.util.UUID;

public record CreateCarePlanRequest(UUID reviewedByClinicianId) {}
