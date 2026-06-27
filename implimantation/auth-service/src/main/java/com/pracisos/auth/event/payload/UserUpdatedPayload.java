package com.pracisos.auth.event.payload;

import java.time.Instant;
import java.util.UUID;

public record UserUpdatedPayload(
    UUID userId,
    UUID tenantId,
    String email,
    String firstName,
    String lastName,
    String role,
    String status,
    Instant updatedAt
) {}
