package com.pracisos.charting.dto.response;

import com.pracisos.charting.domain.entity.ClinicalNote;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NoteResponse(
    UUID noteId,
    UUID patientId,
    UUID practitionerId,
    UUID bookingId,
    String appointmentType,
    ClinicalNote.NoteContent content,
    String status,
    Instant lockedAt,
    String lockedBy,
    Instant createdAt,
    Instant updatedAt,
    List<AmendmentResponse> amendments
) {}
