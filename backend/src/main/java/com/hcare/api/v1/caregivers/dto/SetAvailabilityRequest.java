package com.hcare.api.v1.caregivers.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SetAvailabilityRequest(@Valid @NotNull List<AvailabilityBlockRequest> blocks) {}
