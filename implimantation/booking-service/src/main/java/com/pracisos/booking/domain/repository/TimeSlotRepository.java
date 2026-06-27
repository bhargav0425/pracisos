package com.pracisos.booking.domain.repository;

import com.pracisos.booking.domain.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, UUID> {

    @Query("SELECT s FROM TimeSlot s WHERE s.tenantId = :tenantId AND s.practitionerId = :practitionerId " +
           "AND s.status = 'AVAILABLE' AND s.startTime >= :from AND s.startTime <= :to ORDER BY s.startTime")
    List<TimeSlot> findAvailableSlots(
        @Param("tenantId") UUID tenantId,
        @Param("practitionerId") UUID practitionerId,
        @Param("from") Instant from,
        @Param("to") Instant to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TimeSlot s WHERE s.slotId = :slotId AND s.tenantId = :tenantId")
    Optional<TimeSlot> findByIdWithLock(@Param("slotId") UUID slotId, @Param("tenantId") UUID tenantId);

    List<TimeSlot> findAllByTenantIdAndPractitionerIdAndStartTimeBetween(
        UUID tenantId, UUID practitionerId, Instant start, Instant end);
}
