package com.pracisos.billing.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedPayload {
    private UUID paymentId;
    private UUID invoiceId;
    private UUID tenantId;
    private String stripePaymentIntentId;
    private String failureReason;
    private Instant failedAt;
}
