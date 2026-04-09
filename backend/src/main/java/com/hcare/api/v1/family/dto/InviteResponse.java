package com.hcare.api.v1.family.dto;

public record InviteResponse(
    String inviteUrl,
    String expiresAt   // ISO-8601 UTC timestamp string
) {}
