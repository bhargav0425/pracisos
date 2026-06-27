package com.pracisos.auth.event;

import java.time.Instant;
import java.util.UUID;

public record TenantCreatedEvent(
    UUID tenantId,
    String slug,
    String name,
    Instant createdAt
) {}
