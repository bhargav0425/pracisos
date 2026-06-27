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
    private String eventType;
    private UUID tenantId;
    private String traceId;
    private Instant timestamp;
    private T payload;

    public PracisosEvent(String eventType, UUID tenantId, String traceId, T payload) {
        this.eventType = eventType;
        this.tenantId = tenantId;
        this.traceId = traceId;
        this.payload = payload;
        this.timestamp = Instant.now();
    }
}
