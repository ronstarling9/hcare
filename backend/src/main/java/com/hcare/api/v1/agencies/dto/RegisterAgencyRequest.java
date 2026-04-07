package com.hcare.api.v1.agencies.dto;

import jakarta.validation.constraints.*;

public record RegisterAgencyRequest(
    @NotBlank String agencyName,
    @NotNull @Size(min = 2, max = 2) @Pattern(regexp = "[A-Z]{2}", message = "state must be 2 uppercase letters") String state,
    @NotBlank @Email String adminEmail,
    @NotBlank @Size(min = 8) String adminPassword
) {
    @Override
    public String toString() {
        return "RegisterAgencyRequest[agencyName=" + agencyName + ", state=" + state + ", adminEmail=" + adminEmail + ", adminPassword=[REDACTED]]";
    }
}
