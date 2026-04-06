package com.hcare.api.v1.clients.dto;

import com.hcare.domain.Client;
import com.hcare.domain.ClientStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ClientResponse(
    UUID id,
    UUID agencyId,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String address,
    String phone,
    String medicaidId,
    String serviceState,
    String preferredCaregiverGender,
    String preferredLanguages,
    boolean noPetCaregiver,
    ClientStatus status,
    LocalDateTime createdAt
) {
    public static ClientResponse from(Client c) {
        return new ClientResponse(
            c.getId(), c.getAgencyId(), c.getFirstName(), c.getLastName(),
            c.getDateOfBirth(), c.getAddress(), c.getPhone(), c.getMedicaidId(),
            c.getServiceState(), c.getPreferredCaregiverGender(), c.getPreferredLanguages(),
            c.isNoPetCaregiver(), c.getStatus(), c.getCreatedAt());
    }
}
