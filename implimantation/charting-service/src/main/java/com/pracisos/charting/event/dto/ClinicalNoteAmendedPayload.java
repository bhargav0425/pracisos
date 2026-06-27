package com.pracisos.charting.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalNoteAmendedPayload {
    private UUID noteId;
    private UUID tenantId;
    private UUID amendmentId;
    private UUID practitionerId;
    private String amendmentText;
    private Instant createdAt;
}
