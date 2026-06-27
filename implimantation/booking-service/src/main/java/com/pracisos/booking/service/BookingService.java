package com.pracisos.booking.service;

import com.pracisos.booking.domain.entity.Booking;
import com.pracisos.booking.domain.entity.TimeSlot;
import com.pracisos.booking.domain.repository.BookingRepository;
import com.pracisos.booking.domain.repository.TimeSlotRepository;
import com.pracisos.booking.dto.request.BookingCreateRequest;
import com.pracisos.booking.dto.response.BookingResponse;
import com.pracisos.booking.event.EventPublisher;
import com.pracisos.booking.exception.BookingNotFoundException;
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
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final TimeSlotService timeSlotService;
    private final EventPublisher eventPublisher;

    public BookingResponse createBooking(UUID tenantId, BookingCreateRequest request) {
        // Lock slot with pessimistic locking
        TimeSlot slot = timeSlotService.lockSlot(request.slotId(), tenantId);

        if (!slot.getPractitionerId().equals(request.practitionerId())) {
            timeSlotService.unlockSlot(request.slotId(), tenantId);
            throw new RuntimeException("Slot does not belong to specified practitioner");
        }

        Booking booking = Booking.builder()
            .tenantId(tenantId)
            .slotId(request.slotId())
            .patientId(request.patientId())
            .practitionerId(request.practitionerId())
            .appointmentType(request.appointmentType())
            .notes(request.notes())
            .status("CONFIRMED")
            .build();

        booking = bookingRepository.save(booking);
        log.info("Created booking {} for patient {} with practitioner {}",
            booking.getBookingId(), request.patientId(), request.practitionerId());

        eventPublisher.publishBookingConfirmed(booking, slot);
        return mapToResponse(booking, slot);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByPatient(UUID tenantId, UUID patientId) {
        return bookingRepository.findAllByTenantIdAndPatientId(tenantId, patientId)
            .stream()
            .map(b -> mapToResponse(b, null))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByPractitioner(UUID tenantId, UUID practitionerId) {
        return bookingRepository.findAllByTenantIdAndPractitionerId(tenantId, practitionerId)
            .stream()
            .map(b -> mapToResponse(b, null))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookings(UUID tenantId) {
        return bookingRepository.findAllByTenantId(tenantId)
            .stream()
            .map(b -> mapToResponse(b, null))
            .collect(Collectors.toList());
    }

    public BookingResponse cancelBooking(UUID tenantId, UUID bookingId, String reason) {
        Booking booking = bookingRepository.findByBookingIdAndTenantId(bookingId, tenantId)
            .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        if (!booking.isCancellable()) {
            throw new RuntimeException("Booking cannot be cancelled");
        }

        booking.setStatus("CANCELLED");
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReason(reason);
        booking = bookingRepository.save(booking);

        // Unlock the slot
        timeSlotService.unlockSlot(booking.getSlotId(), tenantId);

        eventPublisher.publishBookingCancelled(booking);
        log.info("Cancelled booking {} for tenant {}", bookingId, tenantId);

        return mapToResponse(booking, null);
    }

    public BookingResponse completeBooking(UUID tenantId, UUID bookingId) {
        Booking booking = bookingRepository.findByBookingIdAndTenantId(bookingId, tenantId)
            .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        if (!booking.isCompletable()) {
            throw new RuntimeException("Booking cannot be completed");
        }

        booking.setStatus("COMPLETED");
        booking.setCompletedAt(Instant.now());
        booking = bookingRepository.save(booking);

        eventPublisher.publishAppointmentCompleted(booking);
        log.info("Completed booking {} for tenant {}", bookingId, tenantId);

        return mapToResponse(booking, null);
    }

    public BookingResponse markNoShow(UUID tenantId, UUID bookingId) {
        Booking booking = bookingRepository.findByBookingIdAndTenantId(bookingId, tenantId)
            .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        if (!booking.isCompletable()) {
            throw new RuntimeException("Booking cannot be marked as no-show");
        }

        booking.setStatus("NO_SHOW");
        booking.setNoShowAt(Instant.now());
        booking = bookingRepository.save(booking);

        eventPublisher.publishAppointmentCompleted(booking); // Triggers billing with penalty
        log.info("Marked no-show for booking {} in tenant {}", bookingId, tenantId);

        return mapToResponse(booking, null);
    }

    private BookingResponse mapToResponse(Booking booking, TimeSlot slot) {
        Instant startTime = slot != null ? slot.getStartTime() : null;
        Instant endTime = slot != null ? slot.getEndTime() : null;

        if (slot == null) {
            // Retrieve slot details from DB if not provided
            slot = timeSlotRepository.findById(booking.getSlotId()).orElse(null);
            if (slot != null) {
                startTime = slot.getStartTime();
                endTime = slot.getEndTime();
            }
        }

        return new BookingResponse(
            booking.getBookingId(),
            booking.getSlotId(),
            booking.getPatientId(),
            booking.getPractitionerId(),
            null, // practitionerName resolved at controller level if needed
            booking.getAppointmentType(),
            booking.getStatus(),
            booking.getNotes(),
            startTime,
            endTime,
            booking.getCreatedAt(),
            booking.getCancelledAt(),
            booking.getCompletedAt()
        );
    }
}
