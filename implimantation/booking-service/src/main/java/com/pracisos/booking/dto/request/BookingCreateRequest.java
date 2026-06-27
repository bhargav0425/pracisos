package com.pracisos.booking.dto.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record BookingCreateRequest(
    @NotNull UUID slotId,
    @NotNull UUID patientId,
    @NotNull UUID practitionerId,
    @NotBlank @Size(max = 50) String appointmentType,
    @Size(max = 500) String notes
) {}
