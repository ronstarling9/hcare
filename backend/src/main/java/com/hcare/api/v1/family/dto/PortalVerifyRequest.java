package com.hcare.api.v1.family.dto;

import jakarta.validation.constraints.NotBlank;

public record PortalVerifyRequest(
    @NotBlank String token
) {}
