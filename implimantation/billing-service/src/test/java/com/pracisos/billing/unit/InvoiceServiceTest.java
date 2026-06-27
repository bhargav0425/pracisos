package com.pracisos.billing.unit;

import com.pracisos.billing.domain.entity.Invoice;
import com.pracisos.billing.domain.repository.InvoiceRepository;
import com.pracisos.billing.dto.response.InvoiceResponse;
import com.pracisos.billing.event.EventPublisher;
import com.pracisos.billing.exception.InvoiceNotFoundException;
import com.pracisos.billing.service.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private InvoiceService invoiceService;

    private UUID tenantId;
    private UUID bookingId;
    private UUID patientId;
    private UUID practitionerId;
    private Invoice testInvoice;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        practitionerId = UUID.randomUUID();

        testInvoice = Invoice.builder()
            .invoiceId(UUID.randomUUID())
            .tenantId(tenantId)
            .bookingId(bookingId)
            .patientId(patientId)
            .practitionerId(practitionerId)
            .amountCents(15000)
            .status("ISSUED")
            .description("CONSULTATION appointment fee")
            .issuedAt(Instant.now())
            .build();
    }

    @Test
    void createInvoiceFromBooking_Success() {
        when(invoiceRepository.findByBookingIdAndTenantId(bookingId, tenantId)).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(testInvoice);

        Invoice result = invoiceService.createInvoiceFromBooking(
            tenantId, bookingId, patientId, practitionerId, "CONSULTATION", false
        );

        assertNotNull(result);
        assertEquals(15000, result.getAmountCents());
        verify(invoiceRepository).save(any(Invoice.class));
        verify(eventPublisher).publishInvoiceCreated(any(Invoice.class));
    }

    @Test
    void createInvoiceFromBooking_Duplicate_ReturnsNull() {
        when(invoiceRepository.findByBookingIdAndTenantId(bookingId, tenantId)).thenReturn(Optional.of(testInvoice));

        Invoice result = invoiceService.createInvoiceFromBooking(
            tenantId, bookingId, patientId, practitionerId, "CONSULTATION", false
        );

        assertNull(result);
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void createInvoiceFromBooking_NoShow_CreatesPenalty() {
        Invoice penaltyInvoice = Invoice.builder()
            .invoiceId(UUID.randomUUID())
            .tenantId(tenantId)
            .bookingId(bookingId)
            .patientId(patientId)
            .practitionerId(practitionerId)
            .amountCents(5000)
            .status("ISSUED")
            .isNoShowPenalty(true)
            .build();

        when(invoiceRepository.findByBookingIdAndTenantId(bookingId, tenantId)).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(penaltyInvoice);

        Invoice result = invoiceService.createInvoiceFromBooking(
            tenantId, bookingId, patientId, practitionerId, "CONSULTATION", true
        );

        assertNotNull(result);
        assertEquals(5000, result.getAmountCents());
        assertTrue(result.getIsNoShowPenalty());
    }

    @Test
    void createCancellationInvoice_Within24h_CreatesLateFee() {
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = invoiceService.createCancellationInvoice(
            tenantId, bookingId, patientId, practitionerId, "CONSULTATION", 12 // 12 hours before
        );

        assertNotNull(result);
        assertEquals(15000, result.getAmountCents());
        assertEquals("Late cancellation fee (within 24h)", result.getDescription());
    }

    @Test
    void createCancellationInvoice_Outside24h_ReturnsNull() {
        Invoice result = invoiceService.createCancellationInvoice(
            tenantId, bookingId, patientId, practitionerId, "CONSULTATION", 36 // 36 hours before
        );

        assertNull(result);
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void cancelInvoice_Success() {
        when(invoiceRepository.findByInvoiceIdAndTenantId(testInvoice.getInvoiceId(), tenantId))
            .thenReturn(Optional.of(testInvoice));

        invoiceService.cancelInvoice(tenantId, testInvoice.getInvoiceId());

        assertEquals("CANCELLED", testInvoice.getStatus());
        assertNotNull(testInvoice.getCancelledAt());
        verify(invoiceRepository).save(testInvoice);
    }

    @Test
    void cancelInvoice_NotIssued_ThrowsException() {
        testInvoice.setStatus("PAID");
        when(invoiceRepository.findByInvoiceIdAndTenantId(testInvoice.getInvoiceId(), tenantId))
            .thenReturn(Optional.of(testInvoice));

        assertThrows(RuntimeException.class, () -> invoiceService.cancelInvoice(tenantId, testInvoice.getInvoiceId()));
    }

    @Test
    void getInvoicesByPatient_ReturnsList() {
        when(invoiceRepository.findAllByTenantIdAndPatientId(tenantId, patientId))
            .thenReturn(Collections.singletonList(testInvoice));

        List<InvoiceResponse> result = invoiceService.getInvoicesByPatient(tenantId, patientId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(15000, result.get(0).amountCents());
    }
}
