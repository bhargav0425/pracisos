package com.pracisos.booking.dto.request;

import jakarta.validation.constraints.*;

public record BookingStatusUpdateRequest(
    @NotBlank String status,
    @Size(max = 255) String reason
) {}
