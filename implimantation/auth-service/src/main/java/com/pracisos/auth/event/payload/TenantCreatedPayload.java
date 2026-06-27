package com.pracisos.auth.event.payload;

import java.time.Instant;
import java.util.UUID;

public record TenantCreatedPayload(
    UUID tenantId,
    String slug,
    String name,
    Instant createdAt
) {}
