package com.pracisos.charting.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingCancelledEvent {
    private UUID bookingId;
    private UUID tenantId;
    private UUID patientId;
    private UUID practitionerId;
    private String cancellationReason;
    private Instant cancelledAt;
}
