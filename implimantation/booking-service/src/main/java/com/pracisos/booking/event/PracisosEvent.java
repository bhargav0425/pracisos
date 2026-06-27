package com.pracisos.booking.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PracisosEvent<T> {
    private UUID eventId;
    private String eventType;
    private UUID tenantId;
    private Instant timestamp;
    private String traceId;
    private T payload;

    public PracisosEvent(String eventType, UUID tenantId, String traceId, T payload) {
        this.eventId = UUID.randomUUID();
        this.eventType = eventType;
        this.tenantId = tenantId;
        this.timestamp = Instant.now();
        this.traceId = traceId;
        this.payload = payload;
    }
}
