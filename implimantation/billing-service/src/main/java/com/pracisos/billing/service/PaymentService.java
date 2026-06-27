package com.pracisos.billing.service;

import com.pracisos.billing.domain.entity.Invoice;
import com.pracisos.billing.domain.entity.Payment;
import com.pracisos.billing.domain.repository.InvoiceRepository;
import com.pracisos.billing.domain.repository.PaymentRepository;
import com.pracisos.billing.dto.response.PaymentResponse;
import com.pracisos.billing.event.EventPublisher;
import com.pracisos.billing.exception.InvoiceNotFoundException;
import com.pracisos.billing.exception.PaymentProcessingException;
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
    private final EventPublisher eventPublisher;

    public PaymentIntent createPaymentIntent(UUID tenantId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)
            .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));

        if (!invoice.isPayable()) {
            throw new PaymentProcessingException("Invoice is not payable (status: " + invoice.getStatus() + ")");
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
            .orElseThrow(() -> new PaymentProcessingException("Payment not found for intent: " + paymentIntentId));

        Invoice invoice = payment.getInvoice();

        if ("succeeded".equals(status)) {
            payment.setStatus("SUCCEEDED");
            invoice.setStatus("PAID");
            invoice.setPaidAt(Instant.now());
            paymentRepository.save(payment);
            invoiceRepository.save(invoice);
            eventPublisher.publishPaymentConfirmed(payment);
        } else if ("payment_failed".equals(status)) {
            payment.setStatus("FAILED");
            paymentRepository.save(payment);
            eventPublisher.publishPaymentFailed(payment, "Stripe payment failed");
        }

        log.info("Processed webhook payment {} -> status: {}", paymentIntentId, status);
        return payment;
    }

    public void simulatePaymentSuccess(UUID tenantId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)
            .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));

        if (invoice.getStripePaymentIntentId() == null) {
            invoice.setStripePaymentIntentId("pi_mock_" + UUID.randomUUID().toString().substring(0, 8));
            invoiceRepository.save(invoice);

            Payment payment = Payment.builder()
                .tenantId(tenantId)
                .invoice(invoice)
                .stripePaymentIntentId(invoice.getStripePaymentIntentId())
                .amountCents(invoice.getAmountCents())
                .status("PENDING")
                .build();
            paymentRepository.save(payment);
        }

        processWebhookPayment(invoice.getStripePaymentIntentId(), "succeeded");
    }

    public Refund processRefund(UUID tenantId, UUID invoiceId, Integer amountCents, String reason) {
        Invoice invoice = invoiceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)
            .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));

        if (!invoice.isRefundable()) {
            throw new PaymentProcessingException("Invoice is not refundable (status: " + invoice.getStatus() + ")");
        }

        if (invoice.getStripePaymentIntentId() == null) {
            throw new PaymentProcessingException("No Stripe payment found for this invoice");
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
