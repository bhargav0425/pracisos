package com.pracisos.charting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddAmendmentRequest(
    @NotNull UUID practitionerId,
    @NotBlank String amendmentText
) {}
