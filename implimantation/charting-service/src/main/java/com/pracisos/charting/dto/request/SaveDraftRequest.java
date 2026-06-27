package com.pracisos.charting.dto.request;

import com.pracisos.charting.domain.entity.ClinicalNote;
import jakarta.validation.constraints.NotNull;

public record SaveDraftRequest(
    @NotNull ClinicalNote.NoteContent content
) {}
