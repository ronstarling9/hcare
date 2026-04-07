package com.hcare.api.v1.users.dto;

import com.hcare.domain.UserRole;
import jakarta.validation.constraints.*;

public record InviteUserRequest(
    @NotBlank @Email String email,
    @NotNull UserRole role,
    @NotBlank @Size(min = 8) String temporaryPassword
) {}
