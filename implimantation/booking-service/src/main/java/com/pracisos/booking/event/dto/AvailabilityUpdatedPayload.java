package com.pracisos.booking.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityUpdatedPayload {
    private UUID tenantId;
    private UUID practitionerId;
    private Instant updatedAt;
}
