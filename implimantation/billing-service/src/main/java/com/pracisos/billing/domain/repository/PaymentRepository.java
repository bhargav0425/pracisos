package com.pracisos.billing.domain.repository;

import com.pracisos.billing.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findAllByInvoiceInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    List<Payment> findAllByTenantIdAndCreatedAtBetween(UUID tenantId, Instant from, Instant to);
}
