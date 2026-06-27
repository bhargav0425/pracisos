package com.pracisos.charting.domain.repository;

import com.pracisos.charting.domain.entity.Amendment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AmendmentRepository extends JpaRepository<Amendment, UUID> {

    List<Amendment> findAllByNoteNoteIdOrderByCreatedAtDesc(UUID noteId);
}
