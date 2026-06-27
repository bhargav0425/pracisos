package com.pracisos.booking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pracisos.booking.event.dto.*;
import com.pracisos.booking.service.PractitionerSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEventConsumer {

    private final PractitionerSyncService syncService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "auth.tenant.created", groupId = "booking-service")
    @Transactional
    public void handleTenantCreated(PracisosEvent<?> event, Acknowledgment ack) {
        log.info("Received TenantCreated event: {}", event.getEventId());
        ack.acknowledge();
    }

    @KafkaListener(topics = "auth.user.created", groupId = "booking-service")
    @Transactional
    public void handleUserCreated(PracisosEvent<?> event, Acknowledgment ack) {
        log.info("Received UserCreated event: {}", event.getEventId());
        UserCreatedEvent payload = objectMapper.convertValue(event.getPayload(), UserCreatedEvent.class);
        syncService.syncUserCreated(payload);
        ack.acknowledge();
    }

    @KafkaListener(topics = "auth.user.updated", groupId = "booking-service")
    @Transactional
    public void handleUserUpdated(PracisosEvent<?> event, Acknowledgment ack) {
        log.info("Received UserUpdated event: {}", event.getEventId());
        UserUpdatedEvent payload = objectMapper.convertValue(event.getPayload(), UserUpdatedEvent.class);
        syncService.syncUserUpdated(payload);
        ack.acknowledge();
    }

    @KafkaListener(topics = "auth.user.deactivated", groupId = "booking-service")
    @Transactional
    public void handleUserDeactivated(PracisosEvent<?> event, Acknowledgment ack) {
        log.info("Received UserDeactivated event: {}", event.getEventId());
        UserDeactivatedEvent payload = objectMapper.convertValue(event.getPayload(), UserDeactivatedEvent.class);
        syncService.syncUserDeactivated(payload);
        ack.acknowledge();
    }
}
