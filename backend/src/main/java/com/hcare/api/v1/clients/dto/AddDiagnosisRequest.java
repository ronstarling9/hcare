package com.hcare.api.v1.clients.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record AddDiagnosisRequest(
    @NotBlank String icd10Code,
    String description,
    LocalDate onsetDate
) {}
