package com.pracisos.billing.dto.response;

import java.time.Instant;
import java.util.UUID;

public record InvoiceResponse(
    UUID invoiceId,
    UUID bookingId,
    UUID patientId,
    UUID practitionerId,
    Integer amountCents,
    String formattedAmount,
    String status,
    String description,
    Instant issuedAt,
    Instant paidAt,
    Instant cancelledAt,
    Instant refundedAt,
    Boolean isNoShowPenalty,
    String stripePaymentIntentId
) {}
