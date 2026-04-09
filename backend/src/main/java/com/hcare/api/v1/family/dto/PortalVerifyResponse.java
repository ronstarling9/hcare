package com.hcare.api.v1.family.dto;

public record PortalVerifyResponse(
    String jwt,
    String clientId,
    String agencyId
) {}
