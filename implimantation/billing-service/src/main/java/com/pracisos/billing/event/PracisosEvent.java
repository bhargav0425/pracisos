package com.pracisos.billing.event;

import java.time.Instant;
import java.util.UUID;

public record PracisosEvent<T>(
    UUID eventId,
    String eventType,
    UUID tenantId,
    Instant timestamp,
    String traceId,
    T payload
) {
    public PracisosEvent(String eventType, UUID tenantId, String traceId, T payload) {
        this(UUID.randomUUID(), eventType, tenantId, Instant.now(), traceId, payload);
    }
}
