# PRACISOS PLATFORM
## BILLING-SERVICE IMPLEMENTATION GUIDE
### Phase 5: Invoices, Payments, Stripe Integration, Revenue Dashboard

---

## DOCUMENT CONTROL

| Field | Value |
|-------|-------|
| **Version** | 1.0.0 |
| **Status** | READY FOR IMPLEMENTATION |
| **Date** | 2026-06-27 |
| **Phase** | 5 of 7 |
| **Service** | billing-service |
| **Scope** | Auto-Invoice Generation, Stripe Payments, Refunds, Revenue Reporting, Webhooks |
| **Prerequisite** | Master Specification v1.0.0, Auth-Service Guide, Booking-Service Guide, Event Wiring Guide |

---

## 1. GOAL

Build the `billing-service` with its frontend slice that enables:
- **Auto-invoice generation** when appointments are completed (via `AppointmentCompletedV1`)
- **No-show penalty invoices** when appointments are marked NO_SHOW
- **Stripe payment processing** — patients pay invoices online
- **Refund logic** — full refund if cancelled >24h before, no refund if <24h
- **Revenue dashboard** for CLINIC_OWNER with filtering by date range
- **Webhook handling** for Stripe payment_intent.succeeded / payment_intent.payment_failed
- **Zero cross-tenant data leakage** — every query filtered by `tenant_id`

**Validation:** Complete appointment -> invoice auto-created -> patient pays -> Stripe webhook updates status -> revenue dashboard reflects payment.

---

## 2. WHAT YOU WILL BUILD

### 2.1 Backend (Java 21 + Spring Boot 3.3 + Lombok)

```
billing-service/
├── pom.xml
├── src/
│   ├── main/java/com/pracisos/billing/
│   │   ├── BillingServiceApplication.java
│   │   ├── config/
│   │   │   ├── SecurityConfig.java
│   │   │   ├── KafkaConsumerConfig.java
│   │   │   └── StripeConfig.java
│   │   ├── controller/
│   │   │   ├── InvoiceController.java
│   │   │   ├── PaymentController.java
│   │   │   ├── RevenueController.java
│   │   │   └── StripeWebhookController.java
│   │   ├── domain/
│   │   │   ├── entity/
│   │   │   │   ├── Invoice.java
│   │   │   │   └── Payment.java
│   │   │   ├── repository/
│   │   │   │   ├── InvoiceRepository.java
│   │   │   │   └── PaymentRepository.java
│   │   │   └── enums/
│   │   │       ├── InvoiceStatus.java
│   │   │       └── PaymentStatus.java
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   │   ├── PaymentIntentRequest.java
│   │   │   │   └── RefundRequest.java
│   │   │   └── response/
│   │   │       ├── InvoiceResponse.java
│   │   │       ├── PaymentResponse.java
│   │   │       └── RevenueResponse.java
│   │   ├── service/
│   │   │   ├── InvoiceService.java
│   │   │   ├── PaymentService.java
│   │   │   ├── RevenueService.java
│   │   │   ├── StripeService.java
│   │   │   └── BookingEventConsumer.java
│   │   ├── event/
│   │   │   ├── EventPublisher.java
│   │   │   ├── InvoiceCreatedEvent.java
│   │   │   ├── PaymentConfirmedEvent.java
│   │   │   └── PaymentFailedEvent.java
│   │   ├── security/
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   └── StripeWebhookFilter.java
│   │   └── exception/
│   │       ├── GlobalExceptionHandler.java
│   │       ├── InvoiceNotFoundException.java
│   │       └── PaymentProcessingException.java
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           ├── V1__init.sql
│           └── V2__add_stripe_fields.sql
└── Dockerfile
```

### 2.2 Frontend (React 19 + Vite + Tailwind — Jane App Inspired)

```
frontend/src/features/billing/
├── components/
│   ├── InvoiceCard.tsx           # Jane-style invoice card with pay button
│   ├── InvoiceList.tsx           # Filterable invoice list
│   ├── PaymentModal.tsx          # Stripe Elements payment modal
│   ├── RevenueChart.tsx          # Recharts bar chart for revenue
│   ├── RevenueDashboard.tsx      # Clinic owner revenue view
│   ├── RefundBadge.tsx           # Refund status indicator
│   └── StatusBadge.tsx           # Invoice status badges
├── api.ts
├── slice.ts
└── types.ts
```

---

## 3. DATABASE DESIGN

### 3.1 Flyway Migration V1__init.sql

```sql
-- Invoices (auto-generated on appointment completion)
CREATE TABLE IF NOT EXISTS invoices (
    invoice_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    booking_id UUID NOT NULL UNIQUE,
    patient_id UUID NOT NULL,
    practitioner_id UUID NOT NULL,
    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    description TEXT,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    paid_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    refund_amount_cents INTEGER,
    stripe_payment_intent_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_invoices_tenant ON invoices(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invoices_booking ON invoices(booking_id);
CREATE INDEX IF NOT EXISTS idx_invoices_patient ON invoices(patient_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON invoices(status);
CREATE INDEX IF NOT EXISTS idx_invoices_issued ON invoices(issued_at);

-- Payments ( Stripe payment attempts)
CREATE TABLE IF NOT EXISTS payments (
    payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    invoice_id UUID NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
    stripe_payment_intent_id VARCHAR(255) NOT NULL,
    amount_cents INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    stripe_response JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payments_invoice ON payments(invoice_id);
CREATE INDEX IF NOT EXISTS idx_payments_stripe ON payments(stripe_payment_intent_id);
CREATE INDEX IF NOT EXISTS idx_payments_tenant ON payments(tenant_id);
```

### 3.2 Flyway Migration V2__add_stripe_fields.sql

```sql
-- Add Stripe customer ID tracking for recurring payments
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(255);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS metadata JSONB DEFAULT '{}';

-- Add cancellation window tracking for refund logic
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS cancellation_window_hours INTEGER;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS is_no_show_penalty BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_invoices_stripe_customer ON invoices(stripe_customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_no_show ON invoices(is_no_show_penalty) WHERE is_no_show_penalty = TRUE;
```

---

## 4. DOMAIN ENTITIES (Lombok)

### 4.1 Invoice Entity

```java
package com.pracisos.billing.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invoice_id", updatable = false, nullable = false)
    private UUID invoiceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ISSUED";

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refund_amount_cents")
    private Integer refundAmountCents;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private java.util.Map<String, Object> metadata = new java.util.HashMap<>();

    @Column(name = "cancellation_window_hours")
    private Integer cancellationWindowHours;

    @Column(name = "is_no_show_penalty", nullable = false)
    @Builder.Default
    private Boolean isNoShowPenalty = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isPayable() {
        return "ISSUED".equals(status) || "OVERDUE".equals(status);
    }

    public boolean isRefundable() {
        return "PAID".equals(status);
    }

    public String getFormattedAmount() {
        return String.format("$%.2f", amountCents / 100.0);
    }
}
```

### 4.2 Payment Entity

```java
package com.pracisos.billing.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_id", updatable = false, nullable = false)
    private UUID paymentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "stripe_payment_intent_id", nullable = false, length = 255)
    private String stripePaymentIntentId;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stripe_response", columnDefinition = "jsonb")
    private java.util.Map<String, Object> stripeResponse;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

---

## 5. REPOSITORIES

### 5.1 InvoiceRepository

```java
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
```

### 5.2 PaymentRepository

```java
package com.pracisos.billing.domain.repository;

import com.pracisos.billing.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findAllByInvoiceInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    List<Payment> findAllByTenantIdAndCreatedAtBetween(UUID tenantId, java.time.Instant from, java.time.Instant to);
}
```

---

## 6. DTOs (Records + Lombok)

### 6.1 Requests

```java
package com.pracisos.billing.dto.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record PaymentIntentRequest(
    @NotNull UUID invoiceId
) {}
```

```java
package com.pracisos.billing.dto.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record RefundRequest(
    @NotNull UUID invoiceId,
    @NotNull @Min(1) Integer amountCents,
    @Size(max = 255) String reason
) {}
```

### 6.2 Responses

```java
package com.pracisos.billing.dto.response;

import java.time.Instant;
import java.util.UUID;

public record InvoiceResponse(
    UUID invoiceId,
    UUID bookingId,
    UUID patientId,
    UUID practitionerId,
    Integer amountCents,
    String formattedAmount,
    String status,
    String description,
    Instant issuedAt,
    Instant paidAt,
    Instant cancelledAt,
    Instant refundedAt,
    Boolean isNoShowPenalty,
    String stripePaymentIntentId
) {}
```

```java
package com.pracisos.billing.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID paymentId,
    UUID invoiceId,
    String stripePaymentIntentId,
    Integer amountCents,
    String status,
    Instant createdAt
) {}
```

```java
package com.pracisos.billing.dto.response;

public record RevenueResponse(
    Long totalRevenueCents,
    String formattedTotalRevenue,
    Long paidInvoiceCount,
    Long pendingRevenueCents,
    String formattedPendingRevenue,
    Long refundedRevenueCents,
    String formattedRefundedRevenue
) {}
```

---

## 7. SERVICES

### 7.1 InvoiceService

```java
package com.pracisos.billing.service;

import com.pracisos.billing.domain.entity.Invoice;
import com.pracisos.billing.domain.repository.InvoiceRepository;
import com.pracisos.billing.dto.response.InvoiceResponse;
import com.pracisos.billing.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final EventPublisher eventPublisher;

    // Fee schedule (cents)
    private static final int CONSULTATION_FEE = 15000;
    private static final int FOLLOW_UP_FEE = 7500;
    private static final int PROCEDURE_FEE = 25000;
    private static final int NO_SHOW_PENALTY = 5000;

    public Invoice createInvoiceFromBooking(UUID tenantId, UUID bookingId, UUID patientId,
                                            UUID practitionerId, String appointmentType, boolean isNoShow) {
        if (invoiceRepository.findByBookingIdAndTenantId(bookingId, tenantId).isPresent()) {
            log.warn("Invoice already exists for booking {}, skipping", bookingId);
            return null;
        }

        int amountCents = isNoShow ? NO_SHOW_PENALTY : getFeeForType(appointmentType);
        String description = isNoShow
            ? "No-show penalty fee"
            : String.format("%s appointment fee", appointmentType);

        Invoice invoice = Invoice.builder()
            .tenantId(tenantId)
            .bookingId(bookingId)
            .patientId(patientId)
            .practitionerId(practitionerId)
            .amountCents(amountCents)
            .status("ISSUED")
            .description(description)
            .isNoShowPenalty(isNoShow)
            .build();

        invoice = invoiceRepository.save(invoice);
        log.info("Created invoice {} for booking {} amount=${}",
            invoice.getInvoiceId(), bookingId, invoice.getFormattedAmount());

        eventPublisher.publishInvoiceCreated(invoice);
        return invoice;
    }

    public Invoice createCancellationInvoice(UUID tenantId, UUID bookingId, UUID patientId,
                                             UUID practitionerId, String appointmentType,
                                             long hoursBeforeCancellation) {
        // Full refund if >24h, no refund if <24h
        boolean eligibleForRefund = hoursBeforeCancellation >= 24;

        if (eligibleForRefund) {
            log.info("Booking {} cancelled >24h in advance -- full refund eligible", bookingId);
            return null; // No invoice needed, original will be cancelled
        }

        // Charge full fee for late cancellation
        int amountCents = getFeeForType(appointmentType);
        Invoice invoice = Invoice.builder()
            .tenantId(tenantId)
            .bookingId(bookingId)
            .patientId(patientId)
            .practitionerId(practitionerId)
            .amountCents(amountCents)
            .status("ISSUED")
            .description("Late cancellation fee (within 24h)")
            .cancellationWindowHours((int) hoursBeforeCancellation)
            .build();

        invoice = invoiceRepository.save(invoice);
        log.info("Created late cancellation invoice {} for booking {}", invoice.getInvoiceId(), bookingId);
        return invoice;
    }

    public void cancelInvoice(UUID tenantId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)
            .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (!"ISSUED".equals(invoice.getStatus())) {
            throw new RuntimeException("Only ISSUED invoices can be cancelled");
        }

        invoice.setStatus("CANCELLED");
        invoice.setCancelledAt(Instant.now());
        invoiceRepository.save(invoice);
        log.info("Cancelled invoice {} for tenant {}", invoiceId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesByPatient(UUID tenantId, UUID patientId) {
        return invoiceRepository.findAllByTenantIdAndPatientId(tenantId, patientId)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> getAllInvoices(UUID tenantId) {
        return invoiceRepository.findAllByTenantId(tenantId)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(UUID tenantId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)
            .orElseThrow(() -> new RuntimeException("Invoice not found"));
        return mapToResponse(invoice);
    }

    private int getFeeForType(String appointmentType) {
        return switch (appointmentType.toUpperCase()) {
            case "CONSULTATION" -> CONSULTATION_FEE;
            case "FOLLOW_UP" -> FOLLOW_UP_FEE;
            case "PROCEDURE" -> PROCEDURE_FEE;
            default -> CONSULTATION_FEE;
        };
    }

    private InvoiceResponse mapToResponse(Invoice i) {
        return new InvoiceResponse(
            i.getInvoiceId(), i.getBookingId(), i.getPatientId(), i.getPractitionerId(),
            i.getAmountCents(), i.getFormattedAmount(), i.getStatus(), i.getDescription(),
            i.getIssuedAt(), i.getPaidAt(), i.getCancelledAt(), i.getRefundedAt(),
            i.getIsNoShowPenalty(), i.getStripePaymentIntentId()
        );
    }
}
```

### 7.2 PaymentService

```java
package com.pracisos.billing.service;

import com.pracisos.billing.domain.entity.Invoice;
import com.pracisos.billing.domain.entity.Payment;
import com.pracisos.billing.domain.repository.InvoiceRepository;
import com.pracisos.billing.domain.repository.PaymentRepository;
import com.pracisos.billing.dto.response.PaymentResponse;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final StripeService stripeService;

    public PaymentIntent createPaymentIntent(UUID tenantId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)
            .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (!invoice.isPayable()) {
            throw new RuntimeException("Invoice is not payable (status: " + invoice.getStatus() + ")");
        }

        PaymentIntent intent = stripeService.createPaymentIntent(
            invoice.getAmountCents(),
            invoice.getStripeCustomerId(),
            invoice.getInvoiceId().toString()
        );

        invoice.setStripePaymentIntentId(intent.getId());
        invoiceRepository.save(invoice);

        // Record pending payment
        Payment payment = Payment.builder()
            .tenantId(tenantId)
            .invoice(invoice)
            .stripePaymentIntentId(intent.getId())
            .amountCents(invoice.getAmountCents())
            .status("PENDING")
            .build();
        paymentRepository.save(payment);

        log.info("Created payment intent {} for invoice {}", intent.getId(), invoiceId);
        return intent;
    }

    public Payment processWebhookPayment(String paymentIntentId, String status) {
        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
            .orElseThrow(() -> new RuntimeException("Payment not found for intent: " + paymentIntentId));

        Invoice invoice = payment.getInvoice();

        if ("succeeded".equals(status)) {
            payment.setStatus("SUCCEEDED");
            invoice.setStatus("PAID");
            invoice.setPaidAt(Instant.now());
        } else if ("payment_failed".equals(status)) {
            payment.setStatus("FAILED");
        }

        paymentRepository.save(payment);
        invoiceRepository.save(invoice);

        log.info("Processed webhook payment {} -> status: {}", paymentIntentId, status);
        return payment;
    }

    public Refund processRefund(UUID tenantId, UUID invoiceId, Integer amountCents, String reason) {
        Invoice invoice = invoiceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)
            .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (!invoice.isRefundable()) {
            throw new RuntimeException("Invoice is not refundable (status: " + invoice.getStatus() + ")");
        }

        if (invoice.getStripePaymentIntentId() == null) {
            throw new RuntimeException("No Stripe payment found for this invoice");
        }

        int refundAmount = amountCents != null ? amountCents : invoice.getAmountCents();
        Refund refund = stripeService.createRefund(invoice.getStripePaymentIntentId(), refundAmount, reason);

        invoice.setStatus("REFUNDED");
        invoice.setRefundedAt(Instant.now());
        invoice.setRefundAmountCents(refundAmount);
        invoiceRepository.save(invoice);

        log.info("Processed refund {} for invoice {} amount={} cents",
            refund.getId(), invoiceId, refundAmount);
        return refund;
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByInvoice(UUID invoiceId) {
        return paymentRepository.findAllByInvoiceInvoiceIdOrderByCreatedAtDesc(invoiceId)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    private PaymentResponse mapToResponse(Payment p) {
        return new PaymentResponse(
            p.getPaymentId(), p.getInvoice().getInvoiceId(),
            p.getStripePaymentIntentId(), p.getAmountCents(),
            p.getStatus(), p.getCreatedAt()
        );
    }
}
```

### 7.3 RevenueService

```java
package com.pracisos.billing.service;

import com.pracisos.billing.domain.repository.InvoiceRepository;
import com.pracisos.billing.dto.response.RevenueResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RevenueService {

    private final InvoiceRepository invoiceRepository;

    public RevenueResponse getRevenue(UUID tenantId, Instant from, Instant to) {
        Long totalRevenue = invoiceRepository.calculateRevenue(tenantId, from, to);
        Long paidCount = invoiceRepository.countPaidInvoices(tenantId, from, to);

        // Calculate pending (issued but not paid)
        Long pendingRevenue = invoiceRepository.findAllByTenantIdAndStatus(tenantId, "ISSUED")
            .stream()
            .mapToLong(Invoice::getAmountCents)
            .sum();

        // Calculate refunded
        Long refundedRevenue = invoiceRepository.findAllByTenantIdAndStatus(tenantId, "REFUNDED")
            .stream()
            .mapToLong(i -> i.getRefundAmountCents() != null ? i.getRefundAmountCents() : 0)
            .sum();

        return new RevenueResponse(
            totalRevenue != null ? totalRevenue : 0,
            formatCents(totalRevenue != null ? totalRevenue : 0),
            paidCount != null ? paidCount : 0,
            pendingRevenue,
            formatCents(pendingRevenue),
            refundedRevenue,
            formatCents(refundedRevenue)
        );
    }

    private String formatCents(long cents) {
        return String.format("$%.2f", cents / 100.0);
    }
}
```

### 7.4 StripeService

```java
package com.pracisos.billing.service;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StripeService {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    public PaymentIntent createPaymentIntent(int amountCents, String customerId, String metadataInvoiceId) {
        try {
            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount((long) amountCents)
                .setCurrency("cad")
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build()
                )
                .putMetadata("invoice_id", metadataInvoiceId);

            if (customerId != null) {
                paramsBuilder.setCustomer(customerId);
            }

            PaymentIntent intent = PaymentIntent.create(paramsBuilder.build());
            log.info("Created Stripe PaymentIntent {} for invoice {}", intent.getId(), metadataInvoiceId);
            return intent;
        } catch (Exception e) {
            log.error("Failed to create Stripe PaymentIntent: {}", e.getMessage(), e);
            throw new RuntimeException("Payment processing failed", e);
        }
    }

    public Refund createRefund(String paymentIntentId, int amountCents, String reason) {
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount((long) amountCents)
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .putMetadata("reason", reason != null ? reason : "Customer request")
                .build();

            Refund refund = Refund.create(params);
            log.info("Created Stripe Refund {} for PaymentIntent {}", refund.getId(), paymentIntentId);
            return refund;
        } catch (Exception e) {
            log.error("Failed to create Stripe Refund: {}", e.getMessage(), e);
            throw new RuntimeException("Refund processing failed", e);
        }
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }
}
```

---

## 8. EVENT CONSUMER (Booking Events)

### 8.1 BookingEventConsumer

```java
package com.pracisos.billing.service;

import com.pracisos.billing.domain.entity.Invoice;
import com.pracisos.billing.domain.repository.InvoiceRepository;
import com.pracisos.billing.event.dto.AppointmentCompletedPayload;
import com.pracisos.billing.event.dto.BookingCancelledPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEventConsumer {

    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;

    @KafkaListener(topics = "booking.completed", groupId = "billing-service")
    @Transactional
    public void handleAppointmentCompleted(AppointmentCompletedPayload event, Acknowledgment ack) {
        log.info("Received AppointmentCompleted: {} status={}", event.getBookingId(), event.getStatus());

        boolean isNoShow = "NO_SHOW".equals(event.getStatus());

        Invoice invoice = invoiceService.createInvoiceFromBooking(
            event.getTenantId(),
            event.getBookingId(),
            event.getPatientId(),
            event.getPractitionerId(),
            event.getAppointmentType(),
            isNoShow
        );

        if (invoice != null) {
            log.info("Auto-generated invoice {} for completed booking {}",
                invoice.getInvoiceId(), event.getBookingId());
        }
        ack.acknowledge();
    }

    @KafkaListener(topics = "booking.cancelled", groupId = "billing-service")
    @Transactional
    public void handleBookingCancelled(BookingCancelledPayload event, Acknowledgment ack) {
        log.info("Received BookingCancelled: {}", event.getBookingId());

        // Check if invoice exists for this booking
        Invoice existingInvoice = invoiceRepository
            .findByBookingIdAndTenantId(event.getBookingId(), event.getTenantId())
            .orElse(null);

        if (existingInvoice != null) {
            // Cancel existing invoice
            if ("ISSUED".equals(existingInvoice.getStatus())) {
                existingInvoice.setStatus("CANCELLED");
                existingInvoice.setCancelledAt(Instant.now());
                invoiceRepository.save(existingInvoice);
                log.info("Cancelled invoice {} for cancelled booking {}",
                    existingInvoice.getInvoiceId(), event.getBookingId());
            }
            ack.acknowledge();
            return;
        }

        // Calculate hours before cancellation
        long hoursBefore = ChronoUnit.HOURS.between(
            Instant.now(), // Approximation -- in production use booking start time
            event.getCancelledAt()
        );
        hoursBefore = Math.abs(hoursBefore);

        if (hoursBefore < 24) {
            // Create late cancellation fee
            Invoice invoice = invoiceService.createCancellationInvoice(
                event.getTenantId(),
                event.getBookingId(),
                event.getPatientId(),
                event.getPractitionerId(),
                "CONSULTATION", // Default -- should come from event
                hoursBefore
            );
            if (invoice != null) {
                log.info("Created late cancellation invoice {} for booking {}",
                    invoice.getInvoiceId(), event.getBookingId());
            }
        } else {
            log.info("Booking {} cancelled >24h in advance -- no fee", event.getBookingId());
        }

        ack.acknowledge();
    }
}
```

## 9. EVENT PUBLISHING

### 9.1 Event DTOs

```java
package com.pracisos.billing.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceCreatedPayload {
    private UUID invoiceId;
    private UUID tenantId;
    private UUID bookingId;
    private UUID patientId;
    private Integer amountCents;
    private String status;
    private String description;
    private Boolean isNoShowPenalty;
    private Instant createdAt;
}
```

```java
package com.pracisos.billing.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmedPayload {
    private UUID paymentId;
    private UUID invoiceId;
    private UUID tenantId;
    private UUID patientId;
    private Integer amountCents;
    private String stripePaymentIntentId;
    private Instant paidAt;
}
```

```java
package com.pracisos.billing.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedPayload {
    private UUID paymentId;
    private UUID invoiceId;
    private UUID tenantId;
    private String stripePaymentIntentId;
    private String failureReason;
    private Instant failedAt;
}
```

### 9.2 Event Publisher

```java
package com.pracisos.billing.event;

import com.pracisos.billing.domain.entity.Invoice;
import com.pracisos.billing.domain.entity.Payment;
import com.pracisos.billing.event.dto.*;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Tracer tracer;

    private String currentTraceId() {
        var span = tracer.currentSpan();
        return span != null ? span.context().traceId() : "no-trace";
    }

    public void publishInvoiceCreated(Invoice invoice) {
        var payload = new InvoiceCreatedPayload(
            invoice.getInvoiceId(), invoice.getTenantId(), invoice.getBookingId(),
            invoice.getPatientId(), invoice.getAmountCents(), invoice.getStatus(),
            invoice.getDescription(), invoice.getIsNoShowPenalty(), invoice.getCreatedAt()
        );
        var event = new PracisosEvent<>("InvoiceCreatedV1", invoice.getTenantId(), currentTraceId(), payload);
        kafkaTemplate.send("billing.invoice.created", invoice.getTenantId().toString(), event);
        log.info("Published InvoiceCreatedV1 for invoice {}", invoice.getInvoiceId());
    }

    public void publishPaymentConfirmed(Payment payment) {
        var payload = new PaymentConfirmedPayload(
            payment.getPaymentId(), payment.getInvoice().getInvoiceId(),
            payment.getTenantId(), payment.getInvoice().getPatientId(),
            payment.getAmountCents(), payment.getStripePaymentIntentId(), Instant.now()
        );
        var event = new PracisosEvent<>("PaymentConfirmedV1", payment.getTenantId(), currentTraceId(), payload);
        kafkaTemplate.send("billing.payment.confirmed", payment.getTenantId().toString(), event);
        log.info("Published PaymentConfirmedV1 for payment {}", payment.getPaymentId());
    }

    public void publishPaymentFailed(Payment payment, String reason) {
        var payload = new PaymentFailedPayload(
            payment.getPaymentId(), payment.getInvoice().getInvoiceId(),
            payment.getTenantId(), payment.getStripePaymentIntentId(),
            reason, Instant.now()
        );
        var event = new PracisosEvent<>("PaymentFailedV1", payment.getTenantId(), currentTraceId(), payload);
        kafkaTemplate.send("billing.payment.failed", payment.getTenantId().toString(), event);
        log.info("Published PaymentFailedV1 for payment {}", payment.getPaymentId());
    }
}
```

---

## 10. CONTROLLERS

### 10.1 InvoiceController

```java
package com.pracisos.billing.controller;

import com.pracisos.billing.dto.response.InvoiceResponse;
import com.pracisos.billing.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('PATIENT', 'CLINIC_OWNER')")
    public ResponseEntity<List<InvoiceResponse>> getInvoices(
        @RequestAttribute("tenantId") UUID tenantId,
        @RequestAttribute("userId") UUID userId,
        @RequestAttribute("role") String role
    ) {
        if ("PATIENT".equals(role)) {
            return ResponseEntity.ok(invoiceService.getInvoicesByPatient(tenantId, userId));
        }
        return ResponseEntity.ok(invoiceService.getAllInvoices(tenantId));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'CLINIC_OWNER')")
    public ResponseEntity<InvoiceResponse> getInvoice(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(invoiceService.getInvoice(tenantId, id));
    }
}
```

### 10.2 PaymentController

```java
package com.pracisos.billing.controller;

import com.pracisos.billing.dto.request.PaymentIntentRequest;
import com.pracisos.billing.dto.request.RefundRequest;
import com.pracisos.billing.dto.response.PaymentResponse;
import com.pracisos.billing.service.PaymentService;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/invoices/{id}/pay")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PaymentIntent> initiatePayment(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id
    ) {
        PaymentIntent intent = paymentService.createPaymentIntent(tenantId, id);
        return ResponseEntity.ok(intent);
    }

    @PostMapping("/invoices/{id}/refund")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    public ResponseEntity<Refund> processRefund(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id,
        @RequestBody RefundRequest request
    ) {
        Refund refund = paymentService.processRefund(tenantId, id, request.amountCents(), request.reason());
        return ResponseEntity.ok(refund);
    }

    @GetMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAnyRole('PATIENT', 'CLINIC_OWNER')")
    public ResponseEntity<List<PaymentResponse>> getPayments(
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(paymentService.getPaymentsByInvoice(id));
    }
}
```

### 10.3 RevenueController

```java
package com.pracisos.billing.controller;

import com.pracisos.billing.dto.response.RevenueResponse;
import com.pracisos.billing.service.RevenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class RevenueController {

    private final RevenueService revenueService;

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    public ResponseEntity<RevenueResponse> getRevenue(
        @RequestAttribute("tenantId") UUID tenantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(revenueService.getRevenue(tenantId, from, to));
    }
}
```

### 10.4 StripeWebhookController

```java
package com.pracisos.billing.controller;

import com.pracisos.billing.service.PaymentService;
import com.pracisos.billing.service.StripeService;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/billing/webhooks")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final PaymentService paymentService;
    private final StripeService stripeService;

    @PostMapping("/stripe")
    public ResponseEntity<Void> handleStripeWebhook(
        @RequestBody String payload,
        @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, stripeService.getWebhookSecret());

            if ("payment_intent.succeeded".equals(event.getType())) {
                PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
                if (intent != null) {
                    paymentService.processWebhookPayment(intent.getId(), "succeeded");
                    log.info("Processed payment_intent.succeeded for {}", intent.getId());
                }
            } else if ("payment_intent.payment_failed".equals(event.getType())) {
                PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
                if (intent != null) {
                    paymentService.processWebhookPayment(intent.getId(), "payment_failed");
                    log.info("Processed payment_intent.payment_failed for {}", intent.getId());
                }
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Stripe webhook processing failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
```

---

## 11. FRONTEND: JANE APP INSPIRED DESIGN

### 11.1 RTK Query API

```typescript
// features/billing/api.ts
import { createApi } from '@reduxjs/toolkit/query/react';
import { baseQuery } from '../../shared/api/baseQuery';

export interface Invoice {
  invoiceId: string;
  bookingId: string;
  patientId: string;
  practitionerId: string;
  amountCents: number;
  formattedAmount: string;
  status: 'ISSUED' | 'PAID' | 'OVERDUE' | 'REFUNDED' | 'CANCELLED';
  description: string;
  issuedAt: string;
  paidAt: string | null;
  cancelledAt: string | null;
  refundedAt: string | null;
  isNoShowPenalty: boolean;
  stripePaymentIntentId: string | null;
}

export interface Payment {
  paymentId: string;
  invoiceId: string;
  stripePaymentIntentId: string;
  amountCents: number;
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REFUNDED';
  createdAt: string;
}

export interface Revenue {
  totalRevenueCents: number;
  formattedTotalRevenue: string;
  paidInvoiceCount: number;
  pendingRevenueCents: number;
  formattedPendingRevenue: string;
  refundedRevenueCents: number;
  formattedRefundedRevenue: string;
}

export const billingApi = createApi({
  reducerPath: 'billingApi',
  baseQuery: baseQuery,
  tagTypes: ['Invoice', 'Payment', 'Revenue'],
  endpoints: (builder) => ({
    getInvoices: builder.query<Invoice[], void>({
      query: () => '/billing/invoices',
      providesTags: ['Invoice'],
    }),
    getInvoice: builder.query<Invoice, string>({
      query: (id) => `/billing/invoices/${id}`,
      providesTags: (result, error, id) => [{ type: 'Invoice', id }],
    }),
    initiatePayment: builder.mutation<any, string>({
      query: (invoiceId) => ({
        url: `/billing/invoices/${invoiceId}/pay`,
        method: 'POST',
      }),
      invalidatesTags: ['Invoice', 'Payment'],
    }),
    getPayments: builder.query<Payment[], string>({
      query: (invoiceId) => `/billing/invoices/${invoiceId}/payments`,
      providesTags: ['Payment'],
    }),
    getRevenue: builder.query<Revenue, { from: string; to: string }>({
      query: ({ from, to }) => `/billing/revenue?from=${from}&to=${to}`,
      providesTags: ['Revenue'],
    }),
  }),
});

export const {
  useGetInvoicesQuery,
  useGetInvoiceQuery,
  useInitiatePaymentMutation,
  useGetPaymentsQuery,
  useGetRevenueQuery,
} = billingApi;
```

### 11.2 InvoiceCard (Jane-Style)

```tsx
// features/billing/components/InvoiceCard.tsx
import { Invoice } from '../api';
import { format } from 'date-fns';

interface Props {
  invoice: Invoice;
  onPay: (invoice: Invoice) => void;
  userRole: string;
}

const statusConfig = {
  ISSUED: { bg: 'bg-primary-50', text: 'text-primary-700', border: 'border-primary-200', label: 'Issued' },
  PAID: { bg: 'bg-success-50', text: 'text-success-700', border: 'border-success-200', label: 'Paid' },
  OVERDUE: { bg: 'bg-red-50', text: 'text-red-700', border: 'border-red-200', label: 'Overdue' },
  REFUNDED: { bg: 'bg-orange-50', text: 'text-orange-700', border: 'border-orange-200', label: 'Refunded' },
  CANCELLED: { bg: 'bg-surface-100', text: 'text-slate-500', border: 'border-surface-200', label: 'Cancelled' },
};

export function InvoiceCard({ invoice, onPay, userRole }: Props) {
  const status = statusConfig[invoice.status];
  const isPayable = invoice.status === 'ISSUED' || invoice.status === 'OVERDUE';

  return (
    <div className={`rounded-jane-lg border ${status.border} bg-white p-5 hover:shadow-md transition-shadow`}>
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-2">
            <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${status.bg} ${status.text}`}>
              {status.label}
            </span>
            {invoice.isNoShowPenalty && (
              <span className="rounded-full bg-orange-100 px-2 py-0.5 text-xs font-medium text-orange-700">
                No-Show Penalty
              </span>
            )}
          </div>
          <h3 className="font-semibold text-slate-800">{invoice.description}</h3>
          <p className="mt-1 text-sm text-slate-500">
            Issued {format(new Date(invoice.issuedAt), 'MMM d, yyyy')}
          </p>
          {invoice.paidAt && (
            <p className="text-sm text-success-600">
              Paid {format(new Date(invoice.paidAt), 'MMM d, yyyy')}
            </p>
          )}
        </div>
        <div className="text-right">
          <p className="text-2xl font-bold text-slate-800">{invoice.formattedAmount}</p>
          {isPayable && userRole === 'PATIENT' && (
            <button
              onClick={() => onPay(invoice)}
              className="mt-2 rounded-jane bg-primary-600 px-4 py-2 text-sm font-semibold text-white hover:bg-primary-700 transition-colors"
            >
              Pay Now
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
```

### 11.3 PaymentModal (Stripe Integration)

```tsx
// features/billing/components/PaymentModal.tsx
import { useState } from 'react';
import { loadStripe } from '@stripe/stripe-js';
import { Elements, PaymentElement, useStripe, useElements } from '@stripe/react-stripe-js';
import { useInitiatePaymentMutation } from '../api';
import type { Invoice } from '../api';

const stripePromise = loadStripe(import.meta.env.VITE_STRIPE_PUBLIC_KEY);

interface Props {
  invoice: Invoice;
  onClose: () => void;
  onSuccess: () => void;
}

function PaymentForm({ clientSecret, onSuccess }: { clientSecret: string; onSuccess: () => void }) {
  const stripe = useStripe();
  const elements = useElements();
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!stripe || !elements) return;

    setIsProcessing(true);
    const { error: submitError } = await stripe.confirmPayment({
      elements,
      confirmParams: { return_url: window.location.href },
      redirect: 'if_required',
    });

    if (submitError) {
      setError(submitError.message || 'Payment failed');
    } else {
      onSuccess();
    }
    setIsProcessing(false);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <PaymentElement />
      {error && <p className="text-sm text-red-600">{error}</p>}
      <button
        type="submit"
        disabled={isProcessing}
        className="w-full rounded-jane bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 disabled:opacity-50"
      >
        {isProcessing ? 'Processing...' : 'Pay Now'}
      </button>
    </form>
  );
}

export function PaymentModal({ invoice, onClose, onSuccess }: Props) {
  const [initiatePayment, { data: paymentIntent, isLoading }] = useInitiatePaymentMutation();
  const [step, setStep] = useState<'init' | 'payment'>('init');

  const handleInitiate = async () => {
    await initiatePayment(invoice.invoiceId);
    setStep('payment');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="mx-4 w-full max-w-md rounded-jane-lg bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-800">Payment</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">Close</button>
        </div>

        <div className="mb-4 rounded-jane bg-surface-50 p-4">
          <p className="text-sm text-slate-600">Amount due</p>
          <p className="text-2xl font-bold text-slate-800">{invoice.formattedAmount}</p>
        </div>

        {step === 'init' && (
          <button
            onClick={handleInitiate}
            disabled={isLoading}
            className="w-full rounded-jane bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 disabled:opacity-50"
          >
            {isLoading ? 'Loading...' : 'Proceed to Payment'}
          </button>
        )}

        {step === 'payment' && paymentIntent?.client_secret && (
          <Elements stripe={stripePromise} options={{ clientSecret: paymentIntent.client_secret }}>
            <PaymentForm clientSecret={paymentIntent.client_secret} onSuccess={onSuccess} />
          </Elements>
        )}
      </div>
    </div>
  );
}
```

### 11.4 RevenueDashboard (Clinic Owner)

```tsx
// features/billing/components/RevenueDashboard.tsx
import { useState } from 'react';
import { useGetRevenueQuery } from '../api';
import { format, subDays, startOfDay, endOfDay } from 'date-fns';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

export function RevenueDashboard() {
  const [dateRange, setDateRange] = useState('30');
  const from = startOfDay(subDays(new Date(), parseInt(dateRange))).toISOString();
  const to = endOfDay(new Date()).toISOString();
  const { data: revenue, isLoading } = useGetRevenueQuery({ from, to });

  const chartData = revenue ? [
    { name: 'Paid', value: revenue.totalRevenueCents / 100, color: '#22c55e' },
    { name: 'Pending', value: revenue.pendingRevenueCents / 100, color: '#0ea5e9' },
    { name: 'Refunded', value: revenue.refundedRevenueCents / 100, color: '#f97316' },
  ] : [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-slate-800">Revenue Dashboard</h2>
        <select
          value={dateRange}
          onChange={(e) => setDateRange(e.target.value)}
          className="rounded-jane border border-surface-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none"
        >
          <option value="7">Last 7 days</option>
          <option value="30">Last 30 days</option>
          <option value="90">Last 90 days</option>
        </select>
      </div>

      {isLoading ? (
        <div className="rounded-jane-lg border border-surface-200 bg-white p-8 text-center">
          <p className="text-slate-500">Loading revenue data...</p>
        </div>
      ) : revenue ? (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="rounded-jane-lg border border-success-200 bg-success-50 p-5">
              <p className="text-sm font-medium text-success-700">Total Revenue</p>
              <p className="mt-1 text-2xl font-bold text-success-800">{revenue.formattedTotalRevenue}</p>
              <p className="text-sm text-success-600">{revenue.paidInvoiceCount} invoices paid</p>
            </div>
            <div className="rounded-jane-lg border border-primary-200 bg-primary-50 p-5">
              <p className="text-sm font-medium text-primary-700">Pending</p>
              <p className="mt-1 text-2xl font-bold text-primary-800">{revenue.formattedPendingRevenue}</p>
              <p className="text-sm text-primary-600">Awaiting payment</p>
            </div>
            <div className="rounded-jane-lg border border-orange-200 bg-orange-50 p-5">
              <p className="text-sm font-medium text-orange-700">Refunded</p>
              <p className="mt-1 text-2xl font-bold text-orange-800">{revenue.formattedRefundedRevenue}</p>
              <p className="text-sm text-orange-600">Total refunds</p>
            </div>
          </div>

          <div className="rounded-jane-lg border border-surface-200 bg-white p-6">
            <h3 className="mb-4 text-sm font-semibold text-slate-700 uppercase tracking-wide">Revenue Breakdown</h3>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e7e5e4" />
                <XAxis dataKey="name" tick={{ fill: '#57534e', fontSize: 12 }} />
                <YAxis tick={{ fill: '#57534e', fontSize: 12 }} tickFormatter={(v) => `$${v}`} />
                <Tooltip
                  formatter={(value: number) => [`$${value.toFixed(2)}`, 'Amount']}
                  contentStyle={{ borderRadius: '0.5rem', border: '1px solid #e7e5e4' }}
                />
                <Bar dataKey="value" fill="#0ea5e9" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </>
      ) : null}
    </div>
  );
}
```

---

## 12. VALIDATION CHECKLIST

| # | Test | Action | Expected Result |
|---|------|--------|-----------------|
| 1 | Auto-invoice on completion | Mark booking as COMPLETED | Invoice created with CONSULTATION fee ($150) |
| 2 | Auto-invoice on no-show | Mark booking as NO_SHOW | Invoice created with penalty fee ($50) |
| 3 | Patient views invoices | GET /billing/invoices | Lists only patient's invoices |
| 4 | Clinic owner views all | GET /billing/invoices (as CLINIC_OWNER) | Lists all tenant invoices |
| 5 | Initiate payment | POST /invoices/{id}/pay | Returns Stripe PaymentIntent client_secret |
| 6 | Stripe webhook success | Simulate payment_intent.succeeded | Invoice status = PAID, payment recorded |
| 7 | Stripe webhook failure | Simulate payment_intent.payment_failed | Payment status = FAILED |
| 8 | Full refund | Cancel >24h before appointment | Invoice cancelled, no new invoice |
| 9 | Late cancellation fee | Cancel <24h before appointment | New invoice created for full fee |
| 10 | Revenue dashboard | GET /billing/revenue?from=X&to=Y | Returns total, pending, refunded amounts |
| 11 | Cross-tenant isolation | Request wrong tenant_id | 403 Forbidden |
| 12 | Role-based access | Patient tries to view revenue | 403 Forbidden |
| 13 | Frontend payment flow | Click Pay -> enter card -> success | Invoice updates to PAID, modal closes |
| 14 | Revenue chart | View dashboard with date filter | Bar chart updates with selected range |

---

## 13. DOCKER COMPOSE UPDATE

Add to docker-compose.yml:

```yaml
  # --- Billing PostgreSQL ---
  postgres-billing:
    image: postgres:16-alpine
    container_name: pracisos-billing-db
    environment:
      POSTGRES_DB: billing_db
      POSTGRES_USER: billing_user
      POSTGRES_PASSWORD: billing_pass
    ports:
      - "5435:5432"
    volumes:
      - billing_postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U billing_user -d billing_db"]
      interval: 5s
      timeout: 5s
      retries: 5

  # --- Billing Service ---
  billing-service:
    build:
      context: ./billing-service
      dockerfile: Dockerfile
    container_name: pracisos-billing-service
    environment:
      DB_HOST: postgres-billing
      DB_PORT: 5432
      DB_NAME: billing_db
      DB_USER: billing_user
      DB_PASSWORD: billing_pass
      JWT_SECRET: ${JWT_SECRET:-change-me-in-production-min-256-bits-long}
      KAFKA_BOOTSTRAP: kafka:29092
      STRIPE_SECRET_KEY: ${STRIPE_SECRET_KEY:-sk_test_placeholder}
      STRIPE_WEBHOOK_SECRET: ${STRIPE_WEBHOOK_SECRET:-whsec_placeholder}
    ports:
      - "8082:8080"
    depends_on:
      postgres-billing:
        condition: service_healthy
      kafka:
        condition: service_healthy
```

---

## 14. STRIPE SETUP NOTES

### 14.1 Environment Variables

```bash
# .env file
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
```

### 14.2 Webhook Endpoint Configuration

In Stripe Dashboard:
- Endpoint URL: `https://your-domain/api/v1/billing/webhooks/stripe`
- Events to listen:
  - `payment_intent.succeeded`
  - `payment_intent.payment_failed`

### 14.3 Local Testing with Stripe CLI

```bash
# Forward webhooks to local dev server
stripe listen --forward-to localhost:8082/api/v1/billing/webhooks/stripe
```

---

## END OF BILLING-SERVICE GUIDE

**Version:** 1.0.0 | **Status:** READY FOR IMPLEMENTATION | **Next:** Phase 6 -- Kubernetes & Istio Mesh
