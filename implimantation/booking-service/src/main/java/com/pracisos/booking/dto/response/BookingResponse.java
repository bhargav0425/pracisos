package com.pracisos.booking.dto.response;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
    UUID bookingId,
    UUID slotId,
    UUID patientId,
    UUID practitionerId,
    String practitionerName,
    String appointmentType,
    String status,
    String notes,
    Instant startTime,
    Instant endTime,
    Instant createdAt,
    Instant cancelledAt,
    Instant completedAt
) {}
