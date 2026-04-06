package com.hcare.api.v1.clients.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddFamilyPortalUserRequest(
    @NotBlank String name,
    @NotBlank @Email String email
) {}
