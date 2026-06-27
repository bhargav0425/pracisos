package com.pracisos.billing.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RefundRequest(
    @NotNull UUID invoiceId,
    @NotNull @Min(1) Integer amountCents,
    @Size(max = 255) String reason
) {}
