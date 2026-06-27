package com.pracisos.booking.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityCreateRequest(
    @NotNull UUID practitionerId,
    @NotNull @Min(0) @Max(6) Integer dayOfWeek,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @NotNull @Min(15) @Max(120) Integer slotDurationMinutes
) {}
