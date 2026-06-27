package com.pracisos.auth.event.payload;

import java.time.Instant;
import java.util.UUID;

public record UserCreatedPayload(
    UUID userId,
    UUID tenantId,
    String email,
    String firstName,
    String lastName,
    String role,
    Instant createdAt
) {}
