package com.pracisos.billing.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PaymentIntentRequest(
    @NotNull UUID invoiceId
) {}
