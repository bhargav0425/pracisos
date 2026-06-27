package com.pracisos.auth.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID userId,
    String email,
    String firstName,
    String lastName,
    String fullName,
    String role,
    String status,
    UUID tenantId,
    Instant createdAt
) {}
