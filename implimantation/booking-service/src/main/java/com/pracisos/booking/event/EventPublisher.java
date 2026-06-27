package com.pracisos.booking.event;

import com.pracisos.booking.domain.entity.Booking;
import com.pracisos.booking.domain.entity.TimeSlot;
import com.pracisos.booking.event.dto.*;
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

    public void publishBookingConfirmed(Booking booking, TimeSlot slot) {
        var payload = new BookingConfirmedPayload(
            booking.getBookingId(), booking.getTenantId(), booking.getPatientId(),
            booking.getPractitionerId(), slot.getSlotId(),
            slot.getStartTime(), slot.getEndTime(), booking.getAppointmentType()
        );
        var event = new PracisosEvent<>("BookingConfirmedV1", booking.getTenantId(), currentTraceId(), payload);
        kafkaTemplate.send("booking.confirmed", booking.getTenantId().toString(), event);
        log.info("Published BookingConfirmedV1 for booking {}", booking.getBookingId());
    }

    public void publishBookingCancelled(Booking booking) {
        var payload = new BookingCancelledPayload(
            booking.getBookingId(), booking.getTenantId(), booking.getPatientId(),
            booking.getPractitionerId(), booking.getCancellationReason(),
            booking.getCancelledAt()
        );
        var event = new PracisosEvent<>("BookingCancelledV1", booking.getTenantId(), currentTraceId(), payload);
        kafkaTemplate.send("booking.cancelled", booking.getTenantId().toString(), event);
        log.info("Published BookingCancelledV1 for booking {}", booking.getBookingId());
    }

    public void publishAppointmentCompleted(Booking booking) {
        var payload = new AppointmentCompletedPayload(
            booking.getBookingId(), booking.getTenantId(), booking.getPatientId(),
            booking.getPractitionerId(), booking.getStatus(),
            Instant.now(), booking.getAppointmentType()
        );
        var event = new PracisosEvent<>("AppointmentCompletedV1", booking.getTenantId(), currentTraceId(), payload);
        kafkaTemplate.send("booking.completed", booking.getTenantId().toString(), event);
        log.info("Published AppointmentCompletedV1 for booking {}", booking.getBookingId());
    }

    public void publishAvailabilityUpdated(UUID tenantId, UUID practitionerId) {
        var payload = new AvailabilityUpdatedPayload(tenantId, practitionerId, Instant.now());
        var event = new PracisosEvent<>("AvailabilityUpdatedV1", tenantId, currentTraceId(), payload);
        kafkaTemplate.send("booking.availability.updated", tenantId.toString(), event);
        log.info("Published AvailabilityUpdatedV1 for practitioner {}", practitionerId);
    }
}
