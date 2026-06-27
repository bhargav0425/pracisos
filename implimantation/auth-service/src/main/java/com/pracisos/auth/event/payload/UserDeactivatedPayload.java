package com.pracisos.auth.event.payload;

import java.time.Instant;
import java.util.UUID;

public record UserDeactivatedPayload(
    UUID userId,
    UUID tenantId,
    String reason,
    Instant deactivatedAt
) {}
