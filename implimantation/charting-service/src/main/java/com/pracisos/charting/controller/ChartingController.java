package com.pracisos.charting.controller;

import com.pracisos.charting.dto.request.AddAmendmentRequest;
import com.pracisos.charting.dto.request.SaveDraftRequest;
import com.pracisos.charting.dto.response.AmendmentResponse;
import com.pracisos.charting.dto.response.NoteResponse;
import com.pracisos.charting.service.ClinicalNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/charting")
@RequiredArgsConstructor
public class ChartingController {

    private final ClinicalNoteService noteService;

    @GetMapping("/notes")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public ResponseEntity<List<NoteResponse>> getNotesByPatient(
        @RequestAttribute("tenantId") UUID tenantId,
        @RequestParam UUID patientId
    ) {
        return ResponseEntity.ok(noteService.getNotesByPatient(tenantId, patientId));
    }

    @GetMapping("/notes/{id}")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public ResponseEntity<NoteResponse> getNote(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(noteService.getNote(tenantId, id));
    }

    @PostMapping("/notes/{id}/draft")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public ResponseEntity<NoteResponse> saveDraft(
        @RequestAttribute("tenantId") UUID tenantId,
        @RequestAttribute("userId") UUID practitionerId,
        @PathVariable UUID id,
        @Valid @RequestBody SaveDraftRequest request
    ) {
        return ResponseEntity.ok(noteService.saveDraft(tenantId, id, practitionerId, request));
    }

    @PostMapping("/notes/{id}/lock")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public ResponseEntity<NoteResponse> lockNote(
        @RequestAttribute("tenantId") UUID tenantId,
        @RequestAttribute("userId") UUID practitionerId,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(noteService.lockNote(tenantId, id, practitionerId));
    }

    @PostMapping("/notes/{id}/amendments")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public ResponseEntity<AmendmentResponse> addAmendment(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id,
        @Valid @RequestBody AddAmendmentRequest request
    ) {
        return ResponseEntity.ok(noteService.addAmendment(tenantId, id, request));
    }
}
