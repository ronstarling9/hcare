package com.hcare.api.v1.caregivers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreateCaregiverRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotBlank @Email String email,
    String phone,
    String address,
    LocalDate hireDate,
    Boolean hasPet
) {}
