package com.hcare.api.v1.users.dto;

import com.hcare.domain.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull UserRole role) {}
