package com.pracisos.auth.event;

import java.time.Instant;
import java.util.UUID;

public record UserCreatedEvent(
    UUID userId,
    UUID tenantId,
    String email,
    String firstName,
    String lastName,
    String role,
    Instant createdAt
) {}
