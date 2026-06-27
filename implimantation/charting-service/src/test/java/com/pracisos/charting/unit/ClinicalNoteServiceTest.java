package com.pracisos.charting.unit;

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
import com.pracisos.charting.service.ClinicalNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicalNoteServiceTest {

    @Mock
    private ClinicalNoteRepository noteRepository;

    @Mock
    private AmendmentRepository amendmentRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ClinicalNoteService noteService;

    private UUID tenantId;
    private UUID patientId;
    private UUID practitionerId;
    private UUID noteId;
    private ClinicalNote draftNote;
    private ClinicalNote lockedNote;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        practitionerId = UUID.randomUUID();
        noteId = UUID.randomUUID();

        draftNote = ClinicalNote.builder()
            .noteId(noteId)
            .tenantId(tenantId)
            .patientId(patientId)
            .practitionerId(practitionerId)
            .bookingId(UUID.randomUUID())
            .appointmentType("Consultation")
            .status("DRAFT")
            .content(new ClinicalNote.NoteContent("S", "O", "A", "P", Collections.emptyList()))
            .createdAt(Instant.now())
            .build();

        lockedNote = ClinicalNote.builder()
            .noteId(noteId)
            .tenantId(tenantId)
            .patientId(patientId)
            .practitionerId(practitionerId)
            .bookingId(UUID.randomUUID())
            .appointmentType("Consultation")
            .status("IMMUTABLE")
            .lockedAt(Instant.now())
            .lockedBy("PRACTITIONER")
            .content(new ClinicalNote.NoteContent("S", "O", "A", "P", Collections.emptyList()))
            .createdAt(Instant.now())
            .build();
    }

    @Test
    void getNotesByPatient_ReturnsList() {
        when(noteRepository.findAllByTenantIdAndPatientId(tenantId, patientId))
            .thenReturn(Collections.singletonList(draftNote));

        List<NoteResponse> list = noteService.getNotesByPatient(tenantId, patientId);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("DRAFT", list.get(0).status());
    }

    @Test
    void getNote_WhenExists_ReturnsNote() {
        when(noteRepository.findByNoteIdAndTenantIdWithAmendments(noteId, tenantId))
            .thenReturn(Optional.of(draftNote));

        NoteResponse response = noteService.getNote(tenantId, noteId);

        assertNotNull(response);
        assertEquals(noteId, response.noteId());
    }

    @Test
    void getNote_WhenNotExists_ThrowsException() {
        when(noteRepository.findByNoteIdAndTenantIdWithAmendments(noteId, tenantId))
            .thenReturn(Optional.empty());

        assertThrows(NoteNotFoundException.class, () -> noteService.getNote(tenantId, noteId));
    }

    @Test
    void saveDraft_Success() {
        SaveDraftRequest request = new SaveDraftRequest(
            new ClinicalNote.NoteContent("S2", "O2", "A2", "P2", Collections.emptyList())
        );
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(draftNote));
        when(noteRepository.save(any(ClinicalNote.class))).thenAnswer(i -> i.getArgument(0));

        NoteResponse response = noteService.saveDraft(tenantId, noteId, practitionerId, request);

        assertNotNull(response);
        assertEquals("S2", response.content().getSubjective());
        verify(noteRepository).save(any(ClinicalNote.class));
    }

    @Test
    void saveDraft_CrossTenant_ThrowsException() {
        SaveDraftRequest request = new SaveDraftRequest(new ClinicalNote.NoteContent());
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(draftNote));

        assertThrows(RuntimeException.class, () -> noteService.saveDraft(UUID.randomUUID(), noteId, practitionerId, request));
    }

    @Test
    void saveDraft_WhenLocked_ThrowsException() {
        SaveDraftRequest request = new SaveDraftRequest(new ClinicalNote.NoteContent());
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(lockedNote));

        assertThrows(NoteLockedException.class, () -> noteService.saveDraft(tenantId, noteId, practitionerId, request));
    }

    @Test
    void lockNote_Success() {
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(draftNote));
        when(noteRepository.save(any(ClinicalNote.class))).thenAnswer(i -> i.getArgument(0));

        NoteResponse response = noteService.lockNote(tenantId, noteId, practitionerId);

        assertNotNull(response);
        assertEquals("IMMUTABLE", response.status());
        assertEquals("PRACTITIONER", response.lockedBy());
        verify(eventPublisher).publishNoteLocked(any(ClinicalNote.class));
    }

    @Test
    void lockNote_AlreadyLocked_ThrowsException() {
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(lockedNote));

        assertThrows(NoteLockedException.class, () -> noteService.lockNote(tenantId, noteId, practitionerId));
    }

    @Test
    void addAmendment_Success() {
        AddAmendmentRequest request = new AddAmendmentRequest(practitionerId, "Correction: Patient is doing fine.");
        Amendment amendment = Amendment.builder()
            .amendmentId(UUID.randomUUID())
            .tenantId(tenantId)
            .note(lockedNote)
            .practitionerId(practitionerId)
            .amendmentText(request.amendmentText())
            .createdAt(Instant.now())
            .build();

        when(noteRepository.findById(noteId)).thenReturn(Optional.of(lockedNote));
        when(amendmentRepository.save(any(Amendment.class))).thenReturn(amendment);

        AmendmentResponse response = noteService.addAmendment(tenantId, noteId, request);

        assertNotNull(response);
        assertEquals(request.amendmentText(), response.amendmentText());
        verify(eventPublisher).publishNoteAmended(any(ClinicalNote.class), any(Amendment.class));
    }

    @Test
    void addAmendment_WhenUnlocked_ThrowsException() {
        AddAmendmentRequest request = new AddAmendmentRequest(practitionerId, "Correction");
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(draftNote));

        assertThrows(RuntimeException.class, () -> noteService.addAmendment(tenantId, noteId, request));
    }
}
