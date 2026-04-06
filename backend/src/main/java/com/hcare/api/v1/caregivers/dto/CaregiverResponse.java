package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CaregiverResponse(
    UUID id,
    UUID agencyId,
    String firstName,
    String lastName,
    String email,
    String phone,
    String address,
    LocalDate hireDate,
    boolean hasPet,
    CaregiverStatus status,
    LocalDateTime createdAt
) {
    public static CaregiverResponse from(Caregiver c) {
        return new CaregiverResponse(
            c.getId(), c.getAgencyId(), c.getFirstName(), c.getLastName(),
            c.getEmail(), c.getPhone(), c.getAddress(), c.getHireDate(),
            c.hasPet(), c.getStatus(), c.getCreatedAt());
    }
}
