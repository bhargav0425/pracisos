package com.pracisos.auth.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
    UUID tenantId,
    String slug,
    String name,
    String status,
    Instant createdAt
) {}
