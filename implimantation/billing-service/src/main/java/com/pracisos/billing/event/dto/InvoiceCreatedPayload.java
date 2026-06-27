package com.pracisos.billing.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceCreatedPayload {
    private UUID invoiceId;
    private UUID tenantId;
    private UUID bookingId;
    private UUID patientId;
    private Integer amountCents;
    private String status;
    private String description;
    private Boolean isNoShowPenalty;
    private Instant createdAt;
}
