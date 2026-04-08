package com.hcare.api.v1.clients.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.domain.Client;
import com.hcare.domain.ClientStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ClientResponse(
    UUID id,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String address,
    String phone,
    String medicaidId,
    String serviceState,
    String preferredCaregiverGender,
    List<String> preferredLanguages,
    boolean noPetCaregiver,
    ClientStatus status,
    LocalDateTime createdAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    public static ClientResponse from(Client c) {
        List<String> langs;
        try {
            langs = MAPPER.readValue(c.getPreferredLanguages(), STRING_LIST);
        } catch (Exception e) {
            langs = List.of();
        }
        return new ClientResponse(
            c.getId(), c.getFirstName(), c.getLastName(),
            c.getDateOfBirth(), c.getAddress(), c.getPhone(), c.getMedicaidId(),
            c.getServiceState(), c.getPreferredCaregiverGender(), langs,
            c.isNoPetCaregiver(), c.getStatus(), c.getCreatedAt());
    }
}
