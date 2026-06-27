package com.pracisos.auth.event;

import com.pracisos.auth.event.payload.*;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

    public void publishTenantCreated(TenantCreatedPayload payload) {
        var event = new PracisosEvent<>("TenantCreatedV1", payload.tenantId(), currentTraceId(), payload);
        send("auth.tenant.created", payload.tenantId().toString(), event);
    }

    public void publishUserCreated(UserCreatedPayload payload) {
        var event = new PracisosEvent<>("UserCreatedV1", payload.tenantId(), currentTraceId(), payload);
        send("auth.user.created", payload.tenantId().toString(), event);
    }

    public void publishUserUpdated(UserUpdatedPayload payload) {
        var event = new PracisosEvent<>("UserUpdatedV1", payload.tenantId(), currentTraceId(), payload);
        send("auth.user.updated", payload.tenantId().toString(), event);
    }

    public void publishUserDeactivated(UserDeactivatedPayload payload) {
        var event = new PracisosEvent<>("UserDeactivatedV1", payload.tenantId(), currentTraceId(), payload);
        send("auth.user.deactivated", payload.tenantId().toString(), event);
    }

    private void send(String topic, String key, Object event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic {}: {}", topic, ex.getMessage(), ex);
            } else {
                log.info("Published event to topic {} partition {} offset {}",
                    topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
