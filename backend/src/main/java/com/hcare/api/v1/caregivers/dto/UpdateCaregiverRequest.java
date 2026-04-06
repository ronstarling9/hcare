package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.CaregiverStatus;
import jakarta.validation.constraints.Email;

import java.time.LocalDate;

public record UpdateCaregiverRequest(
    String firstName,
    String lastName,
    @Email String email,
    String phone,
    String address,
    LocalDate hireDate,
    Boolean hasPet,
    CaregiverStatus status
) {}
