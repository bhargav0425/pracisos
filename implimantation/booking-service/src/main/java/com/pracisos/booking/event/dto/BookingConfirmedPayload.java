package com.pracisos.booking.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmedPayload {
    private UUID bookingId;
    private UUID tenantId;
    private UUID patientId;
    private UUID practitionerId;
    private UUID slotId;
    private Instant startTime;
    private Instant endTime;
    private String appointmentType;
}
