package com.pracisos.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pracisos.billing.domain.entity.Invoice;
import com.pracisos.billing.domain.repository.InvoiceRepository;
import com.pracisos.billing.event.PracisosEvent;
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
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "booking.completed", groupId = "billing-service")
    @Transactional
    public void handleAppointmentCompleted(PracisosEvent<?> event, Acknowledgment ack) {
        log.info("Received AppointmentCompleted event: {}", event.eventId());

        AppointmentCompletedPayload payload = objectMapper.convertValue(event.payload(), AppointmentCompletedPayload.class);

        boolean isNoShow = "NO_SHOW".equals(payload.getStatus());

        Invoice invoice = invoiceService.createInvoiceFromBooking(
            payload.getTenantId(),
            payload.getBookingId(),
            payload.getPatientId(),
            payload.getPractitionerId(),
            payload.getAppointmentType(),
            isNoShow
        );

        if (invoice != null) {
            log.info("Auto-generated invoice {} for completed booking {}",
                invoice.getInvoiceId(), payload.getBookingId());
        }
        ack.acknowledge();
    }

    @KafkaListener(topics = "booking.cancelled", groupId = "billing-service")
    @Transactional
    public void handleBookingCancelled(PracisosEvent<?> event, Acknowledgment ack) {
        log.info("Received BookingCancelled event: {}", event.eventId());

        BookingCancelledPayload payload = objectMapper.convertValue(event.payload(), BookingCancelledPayload.class);

        // Check if invoice exists for this booking
        Invoice existingInvoice = invoiceRepository
            .findByBookingIdAndTenantId(payload.getBookingId(), payload.getTenantId())
            .orElse(null);

        if (existingInvoice != null) {
            // Cancel existing invoice
            if ("ISSUED".equals(existingInvoice.getStatus())) {
                existingInvoice.setStatus("CANCELLED");
                existingInvoice.setCancelledAt(Instant.now());
                invoiceRepository.save(existingInvoice);
                log.info("Cancelled invoice {} for cancelled booking {}",
                    existingInvoice.getInvoiceId(), payload.getBookingId());
            }
            ack.acknowledge();
            return;
        }

        // Calculate hours before cancellation
        long hoursBefore = ChronoUnit.HOURS.between(
            Instant.now(), // Approximation -- in production use booking start time
            payload.getCancelledAt()
        );
        hoursBefore = Math.abs(hoursBefore);

        if (hoursBefore < 24) {
            // Create late cancellation fee
            Invoice invoice = invoiceService.createCancellationInvoice(
                payload.getTenantId(),
                payload.getBookingId(),
                payload.getPatientId(),
                payload.getPractitionerId(),
                "CONSULTATION", // Default -- should come from event
                hoursBefore
            );
            if (invoice != null) {
                log.info("Created late cancellation invoice {} for booking {}",
                    invoice.getInvoiceId(), payload.getBookingId());
            }
        } else {
            log.info("Booking {} cancelled >24h in advance -- no fee", payload.getBookingId());
        }

        ack.acknowledge();
    }
}
