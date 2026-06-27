package com.pracisos.charting.event;

import com.pracisos.charting.domain.entity.Amendment;
import com.pracisos.charting.domain.entity.ClinicalNote;
import com.pracisos.charting.event.dto.ClinicalNoteAmendedPayload;
import com.pracisos.charting.event.dto.ClinicalNoteLockedPayload;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

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

    public void publishNoteLocked(ClinicalNote note) {
        var payload = new ClinicalNoteLockedPayload(
            note.getNoteId(), note.getTenantId(), note.getPatientId(),
            note.getPractitionerId(), note.getBookingId(),
            note.getLockedBy(), note.getLockedAt()
        );
        var event = new PracisosEvent<>("ClinicalNoteLockedV1", note.getTenantId(), currentTraceId(), payload);
        kafkaTemplate.send("charting.note.locked", note.getTenantId().toString(), event);
        log.info("Published ClinicalNoteLockedV1 for note {}", note.getNoteId());
    }

    public void publishNoteAmended(ClinicalNote note, Amendment amendment) {
        var payload = new ClinicalNoteAmendedPayload(
            note.getNoteId(), note.getTenantId(), amendment.getAmendmentId(),
            amendment.getPractitionerId(), amendment.getAmendmentText(),
            amendment.getCreatedAt()
        );
        var event = new PracisosEvent<>("ClinicalNoteAmendedV1", note.getTenantId(), currentTraceId(), payload);
        kafkaTemplate.send("charting.note.amended", note.getTenantId().toString(), event);
        log.info("Published ClinicalNoteAmendedV1 for note {}", note.getNoteId());
    }
}
