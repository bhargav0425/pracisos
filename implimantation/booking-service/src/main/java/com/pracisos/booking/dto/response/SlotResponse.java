package com.pracisos.booking.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SlotResponse(
    UUID slotId,
    UUID practitionerId,
    Instant startTime,
    Instant endTime,
    String status
) {}
