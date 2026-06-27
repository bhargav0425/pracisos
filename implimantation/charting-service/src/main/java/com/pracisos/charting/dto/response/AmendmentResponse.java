package com.pracisos.charting.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AmendmentResponse(
    UUID amendmentId,
    UUID practitionerId,
    String amendmentText,
    Instant createdAt
) {}
