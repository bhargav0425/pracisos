package com.pracisos.charting.service;

import com.pracisos.charting.domain.entity.Amendment;
import com.pracisos.charting.domain.entity.ClinicalNote;
import com.pracisos.charting.domain.repository.AmendmentRepository;
import com.pracisos.charting.domain.repository.ClinicalNoteRepository;
import com.pracisos.charting.dto.request.AddAmendmentRequest;
import com.pracisos.charting.dto.request.SaveDraftRequest;
import com.pracisos.charting.dto.response.AmendmentResponse;
import com.pracisos.charting.dto.response.NoteResponse;
import com.pracisos.charting.event.EventPublisher;
import com.pracisos.charting.exception.NoteLockedException;
import com.pracisos.charting.exception.NoteNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ClinicalNoteService {

    private final ClinicalNoteRepository noteRepository;
    private final AmendmentRepository amendmentRepository;
    private final EventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<NoteResponse> getNotesByPatient(UUID tenantId, UUID patientId) {
        return noteRepository.findAllByTenantIdAndPatientId(tenantId, patientId)
            .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public NoteResponse getNote(UUID tenantId, UUID noteId) {
        ClinicalNote note = noteRepository.findByNoteIdAndTenantIdWithAmendments(noteId, tenantId)
            .orElseThrow(() -> new NoteNotFoundException("Note not found: " + noteId));
        return mapToResponse(note);
    }

    public NoteResponse saveDraft(UUID tenantId, UUID noteId, UUID practitionerId, SaveDraftRequest request) {
        ClinicalNote note = noteRepository.findById(noteId)
            .orElseThrow(() -> new NoteNotFoundException("Note not found: " + noteId));

        if (!note.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Cross-tenant access denied");
        }

        if (note.isLocked()) {
            throw new NoteLockedException("Cannot edit locked note. Use amendments instead.");
        }

        if (!note.getPractitionerId().equals(practitionerId)) {
            throw new RuntimeException("Only the assigned practitioner can edit this note");
        }

        note.setContent(request.content());
        note = noteRepository.save(note);

        log.info("Draft saved for note {} by practitioner {}", noteId, practitionerId);
        return mapToResponse(note);
    }

    public NoteResponse lockNote(UUID tenantId, UUID noteId, UUID practitionerId) {
        ClinicalNote note = noteRepository.findById(noteId)
            .orElseThrow(() -> new NoteNotFoundException("Note not found: " + noteId));

        if (!note.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Cross-tenant access denied");
        }

        if (note.isLocked()) {
            throw new NoteLockedException("Note is already locked");
        }

        note.lock("PRACTITIONER");
        note = noteRepository.save(note);

        eventPublisher.publishNoteLocked(note);
        log.info("Note {} manually locked by practitioner {}", noteId, practitionerId);

        return mapToResponse(note);
    }

    public AmendmentResponse addAmendment(UUID tenantId, UUID noteId, AddAmendmentRequest request) {
        ClinicalNote note = noteRepository.findById(noteId)
            .orElseThrow(() -> new NoteNotFoundException("Note not found: " + noteId));

        if (!note.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Cross-tenant access denied");
        }

        if (!note.isLocked()) {
            throw new RuntimeException("Can only amend locked notes");
        }

        Amendment amendment = Amendment.builder()
            .tenantId(tenantId)
            .note(note)
            .practitionerId(request.practitionerId())
            .amendmentText(request.amendmentText())
            .build();

        amendment = amendmentRepository.save(amendment);
        eventPublisher.publishNoteAmended(note, amendment);

        log.info("Amendment added to note {} by practitioner {}", noteId, request.practitionerId());
        return mapToAmendmentResponse(amendment);
    }

    public void autoLockNote(ClinicalNote note) {
        note.lock("SYSTEM");
        noteRepository.save(note);
        eventPublisher.publishNoteLocked(note);
        log.info("Note {} auto-locked by system (24h expired)", note.getNoteId());
    }

    private NoteResponse mapToResponse(ClinicalNote note) {
        return new NoteResponse(
            note.getNoteId(), note.getPatientId(), note.getPractitionerId(),
            note.getBookingId(), note.getAppointmentType(), note.getContent(),
            note.getStatus(), note.getLockedAt(), note.getLockedBy(),
            note.getCreatedAt(), note.getUpdatedAt(),
            note.getAmendments().stream().map(this::mapToAmendmentResponse).collect(Collectors.toList())
        );
    }

    private AmendmentResponse mapToAmendmentResponse(Amendment amendment) {
        return new AmendmentResponse(
            amendment.getAmendmentId(), amendment.getPractitionerId(),
            amendment.getAmendmentText(), amendment.getCreatedAt()
        );
    }
}
