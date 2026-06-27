package com.pracisos.booking.domain.repository;

import com.pracisos.booking.domain.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("SELECT b FROM Booking b WHERE b.tenantId = :tenantId AND b.patientId = :patientId ORDER BY b.createdAt DESC")
    List<Booking> findAllByTenantIdAndPatientId(@Param("tenantId") UUID tenantId, @Param("patientId") UUID patientId);

    @Query("SELECT b FROM Booking b WHERE b.tenantId = :tenantId AND b.practitionerId = :practitionerId ORDER BY b.createdAt DESC")
    List<Booking> findAllByTenantIdAndPractitionerId(@Param("tenantId") UUID tenantId, @Param("practitionerId") UUID practitionerId);

    @Query("SELECT b FROM Booking b WHERE b.tenantId = :tenantId ORDER BY b.createdAt DESC")
    List<Booking> findAllByTenantId(@Param("tenantId") UUID tenantId);

    Optional<Booking> findByBookingIdAndTenantId(UUID bookingId, UUID tenantId);

    boolean existsBySlotIdAndTenantId(UUID slotId, UUID tenantId);
}
