package com.hcare.api.v1.auth.dto;

import java.util.UUID;

public record LoginResponse(
    String token,
    UUID userId,
    UUID agencyId,
    String role
) {}
