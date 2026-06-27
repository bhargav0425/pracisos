package com.pracisos.billing.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmedPayload {
    private UUID paymentId;
    private UUID invoiceId;
    private UUID tenantId;
    private UUID patientId;
    private Integer amountCents;
    private String stripePaymentIntentId;
    private Instant paidAt;
}
