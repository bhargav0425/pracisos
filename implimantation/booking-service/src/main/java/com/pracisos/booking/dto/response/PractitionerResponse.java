package com.pracisos.booking.dto.response;

import java.util.UUID;

public record PractitionerResponse(
    UUID practitionerId,
    String firstName,
    String lastName,
    String fullName,
    String email,
    String status
) {}
