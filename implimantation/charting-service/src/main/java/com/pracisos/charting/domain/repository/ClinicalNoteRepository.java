package com.pracisos.charting.domain.repository;

import com.pracisos.charting.domain.entity.ClinicalNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, UUID> {

    @Query("SELECT n FROM ClinicalNote n WHERE n.tenantId = :tenantId AND n.patientId = :patientId ORDER BY n.createdAt DESC")
    List<ClinicalNote> findAllByTenantIdAndPatientId(@Param("tenantId") UUID tenantId, @Param("patientId") UUID patientId);

    @Query("SELECT n FROM ClinicalNote n WHERE n.tenantId = :tenantId AND n.practitionerId = :practitionerId ORDER BY n.createdAt DESC")
    List<ClinicalNote> findAllByTenantIdAndPractitionerId(@Param("tenantId") UUID tenantId, @Param("practitionerId") UUID practitionerId);

    @Query("SELECT n FROM ClinicalNote n LEFT JOIN FETCH n.amendments WHERE n.noteId = :noteId AND n.tenantId = :tenantId")
    Optional<ClinicalNote> findByNoteIdAndTenantIdWithAmendments(@Param("noteId") UUID noteId, @Param("tenantId") UUID tenantId);

    @Query("SELECT n FROM ClinicalNote n WHERE n.tenantId = :tenantId AND n.bookingId = :bookingId")
    Optional<ClinicalNote> findByTenantIdAndBookingId(@Param("tenantId") UUID tenantId, @Param("bookingId") UUID bookingId);

    @Query("SELECT n FROM ClinicalNote n WHERE n.status = 'DRAFT' AND n.createdAt < :cutoff")
    List<ClinicalNote> findDraftsOlderThan(@Param("cutoff") Instant cutoff);

    boolean existsByBookingIdAndTenantId(UUID bookingId, UUID tenantId);
}
