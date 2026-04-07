package com.hcare.api.v1.agencies.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAgencyRequest(
    @Size(min = 1) String name,
    @Size(min = 2, max = 2) @Pattern(regexp = "[A-Z]{2}") String state
) {}
