package com.hcare.api.v1.caregivers.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SetAvailabilityRequest(@NotNull List<AvailabilityBlockRequest> blocks) {}
