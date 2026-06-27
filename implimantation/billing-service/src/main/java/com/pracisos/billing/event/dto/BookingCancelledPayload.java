package com.pracisos.billing.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingCancelledPayload {
    private UUID bookingId;
    private UUID tenantId;
    private UUID patientId;
    private UUID practitionerId;
    private String cancellationReason;
    private Instant cancelledAt;
}
