package com.pracisos.billing.domain.repository;

import com.pracisos.billing.domain.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.patientId = :patientId ORDER BY i.issuedAt DESC")
    List<Invoice> findAllByTenantIdAndPatientId(@Param("tenantId") UUID tenantId, @Param("patientId") UUID patientId);

    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId ORDER BY i.issuedAt DESC")
    List<Invoice> findAllByTenantId(@Param("tenantId") UUID tenantId);

    Optional<Invoice> findByInvoiceIdAndTenantId(UUID invoiceId, UUID tenantId);

    Optional<Invoice> findByBookingIdAndTenantId(UUID bookingId, UUID tenantId);

    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.status = :status")
    List<Invoice> findAllByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") String status);

    @Query("SELECT SUM(i.amountCents) FROM Invoice i WHERE i.tenantId = :tenantId AND i.status = 'PAID' AND i.paidAt BETWEEN :from AND :to")
    Long calculateRevenue(@Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.tenantId = :tenantId AND i.status = 'PAID' AND i.paidAt BETWEEN :from AND :to")
    Long countPaidInvoices(@Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to);
}
