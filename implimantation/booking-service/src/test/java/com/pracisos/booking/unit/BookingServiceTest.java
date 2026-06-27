package com.pracisos.booking.unit;

import com.pracisos.booking.domain.entity.Booking;
import com.pracisos.booking.domain.entity.TimeSlot;
import com.pracisos.booking.domain.repository.BookingRepository;
import com.pracisos.booking.domain.repository.TimeSlotRepository;
import com.pracisos.booking.dto.request.BookingCreateRequest;
import com.pracisos.booking.dto.response.BookingResponse;
import com.pracisos.booking.event.EventPublisher;
import com.pracisos.booking.exception.BookingNotFoundException;
import com.pracisos.booking.service.BookingService;
import com.pracisos.booking.service.TimeSlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private TimeSlotRepository timeSlotRepository;
    @Mock
    private TimeSlotService timeSlotService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private BookingService bookingService;

    private UUID tenantId;
    private UUID patientId;
    private UUID practitionerId;
    private UUID slotId;
    private UUID bookingId;
    private TimeSlot timeSlot;
    private Booking booking;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        practitionerId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        timeSlot = TimeSlot.builder()
            .slotId(slotId)
            .tenantId(tenantId)
            .practitionerId(practitionerId)
            .startTime(Instant.now().plusSeconds(3600))
            .endTime(Instant.now().plusSeconds(7200))
            .status("AVAILABLE")
            .build();

        booking = Booking.builder()
            .bookingId(bookingId)
            .tenantId(tenantId)
            .slotId(slotId)
            .patientId(patientId)
            .practitionerId(practitionerId)
            .appointmentType("CONSULTATION")
            .status("CONFIRMED")
            .build();
    }

    @Test
    void createBooking_Success() {
        BookingCreateRequest request = new BookingCreateRequest(
            slotId, patientId, practitionerId, "CONSULTATION", "Please be on time"
        );

        when(timeSlotService.lockSlot(slotId, tenantId)).thenReturn(timeSlot);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setBookingId(bookingId);
            return b;
        });

        BookingResponse result = bookingService.createBooking(tenantId, request);

        assertNotNull(result);
        assertEquals(bookingId, result.bookingId());
        assertEquals("CONFIRMED", result.status());
        verify(timeSlotService).lockSlot(slotId, tenantId);
        verify(bookingRepository).save(any(Booking.class));
        verify(eventPublisher).publishBookingConfirmed(any(), eq(timeSlot));
    }

    @Test
    void createBooking_SlotPractitionerMismatch_ThrowsException() {
        BookingCreateRequest request = new BookingCreateRequest(
            slotId, patientId, UUID.randomUUID(), "CONSULTATION", "Please be on time"
        );

        when(timeSlotService.lockSlot(slotId, tenantId)).thenReturn(timeSlot);

        assertThrows(RuntimeException.class, () -> bookingService.createBooking(tenantId, request));

        verify(timeSlotService).unlockSlot(slotId, tenantId);
        verify(bookingRepository, never()).save(any());
        verify(eventPublisher, never()).publishBookingConfirmed(any(), any());
    }

    @Test
    void cancelBooking_Success() {
        when(bookingRepository.findByBookingIdAndTenantId(bookingId, tenantId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse result = bookingService.cancelBooking(tenantId, bookingId, "Emergency");

        assertNotNull(result);
        assertEquals("CANCELLED", result.status());
        verify(timeSlotService).unlockSlot(slotId, tenantId);
        verify(eventPublisher).publishBookingCancelled(booking);
    }

    @Test
    void cancelBooking_NotFound_ThrowsException() {
        when(bookingRepository.findByBookingIdAndTenantId(bookingId, tenantId)).thenReturn(Optional.empty());

        assertThrows(BookingNotFoundException.class, () -> bookingService.cancelBooking(tenantId, bookingId, "Emergency"));
        verify(timeSlotService, never()).unlockSlot(any(), any());
    }

    @Test
    void completeBooking_Success() {
        when(bookingRepository.findByBookingIdAndTenantId(bookingId, tenantId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse result = bookingService.completeBooking(tenantId, bookingId);

        assertNotNull(result);
        assertEquals("COMPLETED", result.status());
        verify(eventPublisher).publishAppointmentCompleted(booking);
    }

    @Test
    void markNoShow_Success() {
        when(bookingRepository.findByBookingIdAndTenantId(bookingId, tenantId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse result = bookingService.markNoShow(tenantId, bookingId);

        assertNotNull(result);
        assertEquals("NO_SHOW", result.status());
        verify(eventPublisher).publishAppointmentCompleted(booking);
    }
}
