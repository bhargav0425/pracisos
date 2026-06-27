package com.pracisos.billing.event;

import com.pracisos.billing.domain.entity.Invoice;
import com.pracisos.billing.domain.entity.Payment;
import com.pracisos.billing.event.dto.*;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Tracer tracer;

    @Autowired
    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate, 
                          @Autowired(required = false) Tracer tracer) {
        this.kafkaTemplate = kafkaTemplate;
        this.tracer = tracer;
    }

    private String currentTraceId() {
        if (tracer != null) {
            var span = tracer.currentSpan();
            return span != null ? span.context().traceId() : "no-trace";
        }
        return "no-trace";
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
