package com.pracisos.billing.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID paymentId,
    UUID invoiceId,
    String stripePaymentIntentId,
    Integer amountCents,
    String status,
    Instant createdAt
) {}
