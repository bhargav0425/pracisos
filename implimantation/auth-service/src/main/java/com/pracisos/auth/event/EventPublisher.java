package com.pracisos.auth.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTenantCreated(TenantCreatedEvent event) {
        kafkaTemplate.send("auth.tenant.created", event.tenantId().toString(), event);
    }

    public void publishUserCreated(UserCreatedEvent event) {
        kafkaTemplate.send("auth.user.created", event.tenantId().toString(), event);
    }
}
