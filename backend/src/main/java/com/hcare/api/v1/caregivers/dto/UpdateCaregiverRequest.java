package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.CaregiverStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateCaregiverRequest(
    @Size(min = 1) String firstName,
    @Size(min = 1) String lastName,
    @Email String email,
    String phone,
    String address,
    LocalDate hireDate,
    Boolean hasPet,
    CaregiverStatus status
) {}
