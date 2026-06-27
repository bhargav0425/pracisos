package com.pracisos.charting.service;

import com.pracisos.charting.domain.entity.ClinicalNote;
import com.pracisos.charting.domain.repository.ClinicalNoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoLockService {

    private final ClinicalNoteRepository noteRepository;
    private final ClinicalNoteService noteService;

    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void autoLockExpiredDrafts() {
        Instant cutoff = Instant.now().minusSeconds(86400); // 24 hours ago
        List<ClinicalNote> expiredDrafts = noteRepository.findDraftsOlderThan(cutoff);

        log.info("Auto-locking {} expired draft notes", expiredDrafts.size());

        for (ClinicalNote note : expiredDrafts) {
            try {
                noteService.autoLockNote(note);
            } catch (Exception e) {
                log.error("Failed to auto-lock note {}: {}", note.getNoteId(), e.getMessage());
            }
        }
    }
}
