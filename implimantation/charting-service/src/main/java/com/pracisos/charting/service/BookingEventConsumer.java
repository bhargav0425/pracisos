package com.pracisos.charting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pracisos.charting.domain.entity.ClinicalNote;
import com.pracisos.charting.domain.repository.ClinicalNoteRepository;
import com.pracisos.charting.event.PracisosEvent;
import com.pracisos.charting.event.dto.BookingCancelledEvent;
import com.pracisos.charting.event.dto.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEventConsumer {

    private final ClinicalNoteRepository noteRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "booking.confirmed", groupId = "charting-service")
    @Transactional
    public void handleBookingConfirmed(PracisosEvent<?> event, Acknowledgment ack) {
        log.info("Received BookingConfirmed event: {}", event.eventId());

        BookingConfirmedEvent payload = objectMapper.convertValue(event.payload(), BookingConfirmedEvent.class);

        if (noteRepository.existsByBookingIdAndTenantId(payload.getBookingId(), payload.getTenantId())) {
            log.warn("Note already exists for booking {}, skipping", payload.getBookingId());
            ack.acknowledge();
            return;
        }

        ClinicalNote note = ClinicalNote.builder()
            .tenantId(payload.getTenantId())
            .patientId(payload.getPatientId())
            .practitionerId(payload.getPractitionerId())
            .bookingId(payload.getBookingId())
            .appointmentType(payload.getAppointmentType())
            .status("DRAFT")
            .build();

        noteRepository.save(note);
        log.info("Created draft note {} for booking {}", note.getNoteId(), payload.getBookingId());
        ack.acknowledge();
    }

    @KafkaListener(topics = "booking.cancelled", groupId = "charting-service")
    @Transactional
    public void handleBookingCancelled(PracisosEvent<?> event, Acknowledgment ack) {
        log.info("Received BookingCancelled event: {}", event.eventId());

        BookingCancelledEvent payload = objectMapper.convertValue(event.payload(), BookingCancelledEvent.class);

        noteRepository.findByTenantIdAndBookingId(payload.getTenantId(), payload.getBookingId())
            .ifPresent(note -> {
                if ("DRAFT".equals(note.getStatus())) {
                    noteRepository.delete(note);
                    log.info("Deleted draft note {} for cancelled booking {}",
                        note.getNoteId(), payload.getBookingId());
                } else {
                    log.info("Note {} is locked, preserving for records", note.getNoteId());
                }
            });

        ack.acknowledge();
    }
}
