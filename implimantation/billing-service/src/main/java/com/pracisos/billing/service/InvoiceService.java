package com.pracisos.billing.service;

import com.pracisos.billing.domain.entity.Invoice;
import com.pracisos.billing.domain.repository.InvoiceRepository;
import com.pracisos.billing.dto.response.InvoiceResponse;
import com.pracisos.billing.event.EventPublisher;
import com.pracisos.billing.exception.InvoiceNotFoundException;
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
            .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));

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
            .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));
        return mapToResponse(invoice);
    }

    private int getFeeForType(String appointmentType) {
        if (appointmentType == null) return CONSULTATION_FEE;
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
