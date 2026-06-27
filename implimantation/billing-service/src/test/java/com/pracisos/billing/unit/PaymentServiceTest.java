package com.pracisos.billing.unit;

import com.pracisos.billing.domain.entity.Invoice;
import com.pracisos.billing.domain.entity.Payment;
import com.pracisos.billing.domain.repository.InvoiceRepository;
import com.pracisos.billing.domain.repository.PaymentRepository;
import com.pracisos.billing.event.EventPublisher;
import com.pracisos.billing.exception.PaymentProcessingException;
import com.pracisos.billing.service.PaymentService;
import com.pracisos.billing.service.StripeService;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StripeService stripeService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    private UUID tenantId;
    private UUID invoiceId;
    private Invoice testInvoice;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();

        testInvoice = Invoice.builder()
            .invoiceId(invoiceId)
            .tenantId(tenantId)
            .bookingId(UUID.randomUUID())
            .patientId(UUID.randomUUID())
            .practitionerId(UUID.randomUUID())
            .amountCents(15000)
            .status("ISSUED")
            .build();

        testPayment = Payment.builder()
            .paymentId(UUID.randomUUID())
            .tenantId(tenantId)
            .invoice(testInvoice)
            .stripePaymentIntentId("pi_123")
            .amountCents(15000)
            .status("PENDING")
            .build();
    }

    @Test
    void createPaymentIntent_Success() {
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_123");

        when(invoiceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)).thenReturn(Optional.of(testInvoice));
        when(stripeService.createPaymentIntent(anyInt(), any(), anyString())).thenReturn(mockIntent);

        PaymentIntent result = paymentService.createPaymentIntent(tenantId, invoiceId);

        assertNotNull(result);
        assertEquals("pi_123", result.getId());
        assertEquals("pi_123", testInvoice.getStripePaymentIntentId());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void createPaymentIntent_NotPayable_ThrowsException() {
        testInvoice.setStatus("PAID");
        when(invoiceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)).thenReturn(Optional.of(testInvoice));

        assertThrows(PaymentProcessingException.class, () -> paymentService.createPaymentIntent(tenantId, invoiceId));
    }

    @Test
    void processWebhookPayment_Succeeded() {
        when(paymentRepository.findByStripePaymentIntentId("pi_123")).thenReturn(Optional.of(testPayment));

        Payment result = paymentService.processWebhookPayment("pi_123", "succeeded");

        assertNotNull(result);
        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("PAID", testInvoice.getStatus());
        assertNotNull(testInvoice.getPaidAt());
        verify(eventPublisher).publishPaymentConfirmed(testPayment);
    }

    @Test
    void processWebhookPayment_Failed() {
        when(paymentRepository.findByStripePaymentIntentId("pi_123")).thenReturn(Optional.of(testPayment));

        Payment result = paymentService.processWebhookPayment("pi_123", "payment_failed");

        assertNotNull(result);
        assertEquals("FAILED", result.getStatus());
        assertEquals("ISSUED", testInvoice.getStatus()); // remains issued
        verify(eventPublisher).publishPaymentFailed(eq(testPayment), anyString());
    }

    @Test
    void processRefund_Success() {
        testInvoice.setStatus("PAID");
        testInvoice.setStripePaymentIntentId("pi_123");

        Refund mockRefund = mock(Refund.class);
        when(mockRefund.getId()).thenReturn("re_123");

        when(invoiceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)).thenReturn(Optional.of(testInvoice));
        when(stripeService.createRefund(eq("pi_123"), anyInt(), anyString())).thenReturn(mockRefund);

        Refund result = paymentService.processRefund(tenantId, invoiceId, 15000, "Patient cancelled");

        assertNotNull(result);
        assertEquals("re_123", result.getId());
        assertEquals("REFUNDED", testInvoice.getStatus());
        assertNotNull(testInvoice.getRefundedAt());
        assertEquals(15000, testInvoice.getRefundAmountCents());
    }
}
