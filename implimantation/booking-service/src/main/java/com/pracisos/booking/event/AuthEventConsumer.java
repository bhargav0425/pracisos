package com.pracisos.booking.event;

import com.pracisos.booking.domain.entity.Practitioner;
import com.pracisos.booking.domain.repository.PractitionerRepository;
import com.pracisos.booking.event.dto.*;
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

    private final PractitionerRepository practitionerRepository;

    @KafkaListener(topics = "auth.user.created", groupId = "booking-service")
    @Transactional
    public void handleUserCreated(UserCreatedEvent event, Acknowledgment ack) {
        log.info("Received UserCreated event: {} role={}", event.getUserId(), event.getRole());

        if (!"PRACTITIONER".equals(event.getRole()) && !"RECEPTIONIST".equals(event.getRole())) {
            ack.acknowledge();
            return;
        }

        if (practitionerRepository.existsById(event.getUserId())) {
            log.warn("Practitioner {} already exists, skipping", event.getUserId());
            ack.acknowledge();
            return;
        }

        Practitioner practitioner = Practitioner.builder()
            .practitionerId(event.getUserId())
            .tenantId(event.getTenantId())
            .firstName(event.getFirstName())
            .lastName(event.getLastName())
            .email(event.getEmail())
            .status("ACTIVE")
            .build();

        practitionerRepository.save(practitioner);
        log.info("Synced practitioner {} to booking-service", event.getUserId());
        ack.acknowledge();
    }

    @KafkaListener(topics = "auth.user.updated", groupId = "booking-service")
    @Transactional
    public void handleUserUpdated(UserUpdatedEvent event, Acknowledgment ack) {
        log.info("Received UserUpdated event: {}", event.getUserId());

        practitionerRepository.findById(event.getUserId()).ifPresentOrElse(
            practitioner -> {
                practitioner.setFirstName(event.getFirstName());
                practitioner.setLastName(event.getLastName());
                practitioner.setEmail(event.getEmail());
                if (event.getStatus() != null) {
                    practitioner.setStatus(event.getStatus());
                }
                practitionerRepository.save(practitioner);
                log.info("Updated practitioner {} in booking-service", event.getUserId());
            },
            () -> log.warn("Practitioner {} not found for update", event.getUserId())
        );
        ack.acknowledge();
    }

    @KafkaListener(topics = "auth.user.deactivated", groupId = "booking-service")
    @Transactional
    public void handleUserDeactivated(UserDeactivatedEvent event, Acknowledgment ack) {
        log.info("Received UserDeactivated event: {}", event.getUserId());

        practitionerRepository.findById(event.getUserId()).ifPresent(practitioner -> {
            practitioner.setStatus("INACTIVE");
            practitionerRepository.save(practitioner);
            log.info("Deactivated practitioner {} in booking-service", event.getUserId());
        });
        ack.acknowledge();
    }
}
